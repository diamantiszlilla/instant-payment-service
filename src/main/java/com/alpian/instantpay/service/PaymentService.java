package com.alpian.instantpay.service;

import com.alpian.instantpay.api.dto.PaymentRequest;
import com.alpian.instantpay.api.dto.PaymentResponse;
import com.alpian.instantpay.infrastructure.persistence.entity.AccountEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.OutboxEventEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.TransactionEntity;
import com.alpian.instantpay.infrastructure.persistence.repository.AccountRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.OutboxEventRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.TransactionRepository;
import com.alpian.instantpay.service.exception.AccountNotFoundException;
import com.alpian.instantpay.service.exception.IdempotencyException;
import com.alpian.instantpay.service.exception.InsufficientFundsException;
import com.alpian.instantpay.service.mapper.PaymentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    public static final int EXPECTED_SCALE = 2;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse sendMoney(PaymentRequest request, UUID idempotencyKey, String senderUsername) {
        log.info("payment_send requested: sender={}, senderAccount={}, recip={}, amount={}, currency={}, idemKey={}",
                senderUsername,
                maskUuid(request.senderAccountId()),
                maskUuid(request.recipientAccountId()),
                request.amount(),
                request.currency(),
                truncateIdem(idempotencyKey)
        );

        transactionRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
            log.warn("duplicate_idempotency_key: idemKey={}", truncateIdem(idempotencyKey));
            throw new IdempotencyException("Transaction already processed");
        });

        AccountEntity senderAccount = accountRepository.findByIdForUpdate(request.senderAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Sender account not found: " + request.senderAccountId()));

        if (!senderAccount.getUser().getUsername().equals(senderUsername)) {
            log.warn("authorization_failure: user '{}' attempted to use account '{}' which is not theirs.",
                    senderUsername, maskUuid(senderAccount.getId()));
            throw new AccessDeniedException("User does not own this account");
        }

        AccountEntity recipientAccount = accountRepository.findByIdForUpdate(request.recipientAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Recipient account not found: " + request.recipientAccountId()));

        ensureCurrenciesMatch(senderAccount.getCurrency(), request.currency(), "sender");
        ensureCurrenciesMatch(recipientAccount.getCurrency(), request.currency(), "recipient");
        ensureDifferentAccounts(senderAccount.getId(), recipientAccount.getId());
        ensurePositiveAmount(request.amount());
        ensureValidCurrencyScale(request.amount(), request.currency());

        BigDecimal newSenderBalance = senderAccount.getBalance().subtract(request.amount());
        if (newSenderBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("insufficient_funds: accountId={}, balance={}, requested={}",
                    maskUuid(senderAccount.getId()),
                    senderAccount.getBalance(),
                    request.amount()
            );
            throw new InsufficientFundsException("Insufficient funds");
        }

        senderAccount.setBalance(newSenderBalance);
        recipientAccount.setBalance(recipientAccount.getBalance().add(request.amount()));

        TransactionEntity tx = TransactionEntity.builder()
                .senderAccount(senderAccount)
                .recipientAccount(recipientAccount)
                .amount(request.amount())
                .currency(request.currency())
                .status(TransactionEntity.TransactionStatus.COMPLETED)
                .idempotencyKey(idempotencyKey)
                .build();

        accountRepository.save(senderAccount);
        accountRepository.save(recipientAccount);
        transactionRepository.save(tx);

        OutboxEventEntity outbox = createOutboxEvent(tx);
        outboxEventRepository.save(outbox);

        log.info("payment_processed: txId={}, amount={}, currency={}",
                maskUuid(tx.getId()), tx.getAmount(), tx.getCurrency());

        return paymentMapper.toPaymentResponse(tx);
    }

    private OutboxEventEntity createOutboxEvent(TransactionEntity transaction) {
        try {
            String payload = objectMapper.writeValueAsString(createTransactionEvent(transaction));
            return OutboxEventEntity.builder()
                    .aggregateType("Transaction")
                    .aggregateId(transaction.getId())
                    .eventTopic("payment.completed")
                    .payload(payload)
                    .status(OutboxEventEntity.EventStatus.PENDING)
                    .build();
        } catch (Exception e) {
            log.error("outbox_creation_failed: txId={}", maskUuid(transaction.getId()), e);
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }

    private TransactionEvent createTransactionEvent(TransactionEntity transaction) {
        return new TransactionEvent(
                transaction.getId(),
                transaction.getSenderAccount().getId(),
                transaction.getRecipientAccount().getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus().name(),
                transaction.getCreatedAt()
        );
    }

    private record TransactionEvent(
            UUID transactionId,
            UUID senderAccountId,
            UUID recipientAccountId,
            BigDecimal amount,
            String currency,
            String status,
            java.time.OffsetDateTime createdAt
    ) {
    }

    private void ensureCurrenciesMatch(String actual, String expected, String role) {
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("Currency mismatch for " + role + ": " + actual + " vs " + expected);
        }
    }

    private void ensureDifferentAccounts(UUID senderId, UUID recipientId) {
        if (senderId.equals(recipientId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
    }

    private void ensurePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private void ensureValidCurrencyScale(BigDecimal amount, String currency) {
        if (amount.scale() > EXPECTED_SCALE) {
            log.warn("invalid_amount_scale: scale={}, expected={}", amount.scale(), EXPECTED_SCALE);
            throw new IllegalArgumentException("Amount scale (" + amount.scale() +
                    ") exceeds the allowed scale for currency " + currency + " (" + EXPECTED_SCALE + ")");
        }
    }

    private String truncateIdem(UUID key) {
        if (key == null) return null;
        String s = key.toString();
        return s.substring(0, 8) + "..." + s.substring(s.length() - 4);
    }

    private String maskUuid(UUID id) {
        if (id == null) return null;
        String s = id.toString();
        return s.substring(0, 8) + "****" + s.substring(s.length() - 4);
    }
}
