package com.alpian.instantpay.service.mapper;

import com.alpian.instantpay.api.dto.PaymentResponse;
import com.alpian.instantpay.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {
    
    public PaymentResponse toPaymentResponse(TransactionEntity transactionEntity) {
        return new PaymentResponse(
            transactionEntity.getId(),
            transactionEntity.getSenderAccount().getId(),
            transactionEntity.getRecipientAccount().getId(),
            transactionEntity.getAmount(),
            transactionEntity.getCurrency(),
            transactionEntity.getStatus().name(),
            transactionEntity.getCreatedAt()
        );
    }
}

