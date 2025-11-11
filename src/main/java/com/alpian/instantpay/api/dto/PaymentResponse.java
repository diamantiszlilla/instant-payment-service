package com.alpian.instantpay.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID transactionId,
        UUID senderAccountId,
        UUID recipientAccountId,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime createdAt
) {
}

