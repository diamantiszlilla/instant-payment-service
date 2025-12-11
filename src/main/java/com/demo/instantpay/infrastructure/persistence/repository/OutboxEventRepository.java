package com.demo.instantpay.infrastructure.persistence.repository;

import com.demo.instantpay.infrastructure.persistence.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findByStatus(OutboxEventEntity.EventStatus status, Pageable pageable);
}
