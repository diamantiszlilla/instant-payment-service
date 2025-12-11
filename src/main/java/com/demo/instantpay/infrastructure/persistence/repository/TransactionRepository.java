package com.demo.instantpay.infrastructure.persistence.repository;

import com.demo.instantpay.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsByIdempotencyKey(UUID idempotencyKey);
}
