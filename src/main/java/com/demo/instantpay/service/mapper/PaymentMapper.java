package com.demo.instantpay.service.mapper;

import com.demo.instantpay.api.dto.PaymentResponse;
import com.demo.instantpay.infrastructure.persistence.entity.TransactionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

    @Mapping(source = "id", target = "transactionId")
    @Mapping(source = "senderAccount.id", target = "senderAccountId")
    @Mapping(source = "recipientAccount.id", target = "recipientAccountId")
    @Mapping(source = "status", target = "status", qualifiedByName = "statusToString")
    PaymentResponse toPaymentResponse(TransactionEntity transactionEntity);

    @org.mapstruct.Named("statusToString")
    default String statusToString(TransactionEntity.TransactionStatus status) {
        return status != null ? status.name() : null;
    }
}
