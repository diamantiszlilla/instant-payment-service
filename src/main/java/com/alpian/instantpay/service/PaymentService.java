package com.alpian.instantpay.service;

import com.alpian.instantpay.api.dto.PaymentRequest;
import com.alpian.instantpay.api.dto.PaymentResponse;
import com.alpian.instantpay.infrastructure.persistence.entity.AccountEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.UserEntity;
import com.alpian.instantpay.infrastructure.persistence.repository.AccountRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.OutboxEventRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.TransactionRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.UserRepository;
import com.alpian.instantpay.service.exception.InsufficientFundsException;
import com.alpian.instantpay.service.mapper.PaymentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.login.AccountNotFoundException;
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
    public PaymentResponse sendMoney(PaymentRequest request, UUID idempotencyKey, String senderUsername) throws AccountNotFoundException {
        log.info("payment_send requested: sender={}, recip={}, amount={}, currency={}, idemKey={}",
                senderUsername, request.recipientAccountId(), request.amount(), request.currency(), truncateIdem(idempotencyKey));

        UserEntity sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new AccountNotFoundException("User not found: " + senderUsername));

        List<AccountEntity> senderAccounts = accountRepository.findByUserId(sender.getId());
        if (senderAccounts.isEmpty()) {
            throw new AccountNotFoundException("No account found for user: " + senderUsername);
        }
        AccountEntity senderAccount = senderAccounts.get(0);

        AccountEntity recipientAccount = accountRepository.findById(request.recipientAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Recipient account not found: " + request.recipientAccountId()));

        ensureCurrenciesMatch(senderAccount.getCurrency(), request.currency(), "sender");
        ensureCurrenciesMatch(recipientAccount.getCurrency(), request.currency(), "recipient");
        ensureDifferentAccounts(senderAccount.getId(), recipientAccount.getId());
        ensurePositiveAmount(request.amount());

        BigDecimal newSenderBalance = senderAccount.getBalance().subtract(request.amount());
        if (newSenderBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("insufficient_funds: accountId={}, balance={}, requested={}", senderAccount.getId(), senderAccount.getBalance(), request.amount());
            throw new InsufficientFundsException("Insufficient funds");
        }

        senderAccount.setBalance(newSenderBalance);
        recipientAccount.setBalance(recipientAccount.getBalance().add(request.amount()));

        throw new UnsupportedOperationException("WIP");
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
}
