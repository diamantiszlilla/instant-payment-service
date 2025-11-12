package com.alpian.instantpay.infrastructure.messaging;

import com.alpian.instantpay.infrastructure.persistence.entity.OutboxEventEntity;
import com.alpian.instantpay.infrastructure.persistence.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaNotificationProducer kafkaNotificationProducer;

    @Value("${outbox.poll.batch-size:100}")
    private int batchSize;

    @Transactional
    @Scheduled(fixedDelayString = "${outbox.poll.delay:5000}")
    public void processOutboxEvents() {
        log.debug("Polling outbox events for processing");

        Pageable pageable = PageRequest.of(0, batchSize);
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findByStatus(
                OutboxEventEntity.EventStatus.PENDING,
                pageable
        );

        if (pendingEvents.isEmpty()) {
            log.trace("No pending outbox events found");
            return;
        }

        log.info("Processing {} pending outbox events", pendingEvents.size());

        for (OutboxEventEntity event : pendingEvents) {
            try {
                log.debug("Processing outbox event: id={}, topic={}", event.getId(), event.getEventTopic());

                kafkaNotificationProducer.sendNotification(event.getEventTopic(), event.getPayload());

                outboxEventRepository.delete(event);

                log.info("Successfully processed outbox event: id={}, topic={}",
                        event.getId(), event.getEventTopic());

            } catch (Exception e) {
                log.error("Failed to process outbox event: id={}, topic={}. Will retry on next poll.",
                        event.getId(), event.getEventTopic(), e);
            }
        }
    }
}
