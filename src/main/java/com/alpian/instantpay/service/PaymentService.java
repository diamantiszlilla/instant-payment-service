package com.alpian.instantpay.service;

import com.alpian.instantpay.api.dto.PaymentRequest;
import com.alpian.instantpay.api.dto.PaymentResponse;
import com.alpian.instantpay.infrastructure.persistence.repository.AccountRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.OutboxEventRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.TransactionRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.UserRepository;
import com.alpian.instantpay.service.mapper.PaymentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return new PaymentResponse(null, null, null, null, null, null, null);
    }
}
