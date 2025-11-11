package com.alpian.instantpay.service;

import com.alpian.instantpay.api.dto.PaymentRequest;
import com.alpian.instantpay.api.dto.PaymentResponse;
import com.alpian.instantpay.infrastructure.persistence.entity.AccountEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.OutboxEventEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.TransactionEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.UserEntity;
import com.alpian.instantpay.infrastructure.persistence.repository.AccountRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.OutboxEventRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.TransactionRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.UserRepository;
import com.alpian.instantpay.service.exception.AccountNotFoundException;
import com.alpian.instantpay.service.exception.IdempotencyException;
import com.alpian.instantpay.service.exception.InsufficientFundsException;
import com.alpian.instantpay.service.mapper.PaymentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final UserRepository userRepository;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse sendMoney(PaymentRequest request, UUID idempotencyKey, String senderUsername) {
        log.info("payment_send requested: sender={}, recip={}, amount={}, currency={}, idemKey={}",
                senderUsername,
                maskUuid(request.recipientAccountId()),
                request.amount(),
                request.currency(),
                truncateIdem(idempotencyKey)
        );

        transactionRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
            log.warn("duplicate_idempotency_key: idemKey={}", truncateIdem(idempotencyKey));
            throw new IdempotencyException("Transaction already processed");
        });

        UserEntity sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new AccountNotFoundException("User not found: " + senderUsername));

        List<AccountEntity> senderAccounts = accountRepository.findByUserId(sender.getId());
        if (senderAccounts.isEmpty()) {
            throw new AccountNotFoundException("No account found for user: " + senderUsername);
        }
        AccountEntity senderAccount = senderAccounts.getFirst();

        AccountEntity recipientAccount = accountRepository.findById(request.recipientAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Recipient account not found: " + request.recipientAccountId()));

        ensureCurrenciesMatch(senderAccount.getCurrency(), request.currency(), "sender");
        ensureCurrenciesMatch(recipientAccount.getCurrency(), request.currency(), "recipient");
        ensureDifferentAccounts(senderAccount.getId(), recipientAccount.getId());
        ensurePositiveAmount(request.amount());

        var newSenderBalance = senderAccount.getBalance().subtract(request.amount());
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

        var outbox = createOutboxEvent(tx);
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
