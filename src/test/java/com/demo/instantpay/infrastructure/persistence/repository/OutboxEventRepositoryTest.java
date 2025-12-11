package com.demo.instantpay.infrastructure.persistence.repository;

import com.demo.instantpay.AbstractDbIntegrationTest;
import com.demo.instantpay.infrastructure.persistence.entity.OutboxEventEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("OutboxEventRepository Tests")
@EnableJpaRepositories(basePackages = "com.alpian.instantpay.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.alpian.instantpay.infrastructure.persistence.entity")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=true"
})
class OutboxEventRepositoryTest extends AbstractDbIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = createPostgresContainer();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registerDatasourceProperties(registry, POSTGRES);
    }

    @BeforeAll
    static void startPostgresContainer() {
        startContainer(POSTGRES);
    }

    @AfterAll
    static void stopPostgresContainer() {
        stopContainer(POSTGRES);
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventEntity pendingEvent1;
    private OutboxEventEntity pendingEvent2;
    private OutboxEventEntity sentEvent;
    private OutboxEventEntity failedEvent;

    @BeforeEach
    void setUp() {
        pendingEvent1 = OutboxEventEntity.builder()
                .aggregateType("Transaction")
                .aggregateId(UUID.randomUUID())
                .eventTopic("payment.processed")
                .payload("{\"transactionId\":\"123\",\"amount\":100.00}")
                .status(OutboxEventEntity.EventStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
        pendingEvent1 = outboxEventRepository.save(pendingEvent1);

        pendingEvent2 = OutboxEventEntity.builder()
                .aggregateType("Transaction")
                .aggregateId(UUID.randomUUID())
                .eventTopic("payment.processed")
                .payload("{\"transactionId\":\"456\",\"amount\":200.00}")
                .status(OutboxEventEntity.EventStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
        pendingEvent2 = outboxEventRepository.save(pendingEvent2);

        sentEvent = OutboxEventEntity.builder()
                .aggregateType("Transaction")
                .aggregateId(UUID.randomUUID())
                .eventTopic("payment.processed")
                .payload("{\"transactionId\":\"789\",\"amount\":300.00}")
                .status(OutboxEventEntity.EventStatus.SENT)
                .createdAt(OffsetDateTime.now())
                .processedAt(OffsetDateTime.now())
                .build();
        sentEvent = outboxEventRepository.save(sentEvent);

        failedEvent = OutboxEventEntity.builder()
                .aggregateType("Transaction")
                .aggregateId(UUID.randomUUID())
                .eventTopic("payment.processed")
                .payload("{\"transactionId\":\"999\",\"amount\":400.00}")
                .status(OutboxEventEntity.EventStatus.FAILED)
                .createdAt(OffsetDateTime.now())
                .build();
        failedEvent = outboxEventRepository.save(failedEvent);
    }

    @Test
    @DisplayName("Should find events by status")
    void shouldFindEventsByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findByStatus(
                OutboxEventEntity.EventStatus.PENDING, pageable);
        List<OutboxEventEntity> sentEvents = outboxEventRepository.findByStatus(
                OutboxEventEntity.EventStatus.SENT, pageable);
        List<OutboxEventEntity> failedEvents = outboxEventRepository.findByStatus(
                OutboxEventEntity.EventStatus.FAILED, pageable);

        assertThat(pendingEvents).hasSize(2);
        assertThat(pendingEvents).extracting(OutboxEventEntity::getId)
                .containsExactlyInAnyOrder(pendingEvent1.getId(), pendingEvent2.getId());
        assertThat(pendingEvents).extracting(OutboxEventEntity::getStatus)
                .containsOnly(OutboxEventEntity.EventStatus.PENDING);

        assertThat(sentEvents).hasSize(1);
        assertThat(sentEvents).extracting(OutboxEventEntity::getId)
                .containsExactly(sentEvent.getId());
        assertThat(sentEvents).extracting(OutboxEventEntity::getStatus)
                .containsOnly(OutboxEventEntity.EventStatus.SENT);

        assertThat(failedEvents).hasSize(1);
        assertThat(failedEvents).extracting(OutboxEventEntity::getId)
                .containsExactly(failedEvent.getId());
        assertThat(failedEvents).extracting(OutboxEventEntity::getStatus)
                .containsOnly(OutboxEventEntity.EventStatus.FAILED);
    }

    @Test
    @DisplayName("Should respect pagination when finding events by status")
    void shouldRespectPaginationWhenFindingEventsByStatus() {
        for (int i = 0; i < 5; i++) {
            OutboxEventEntity event = OutboxEventEntity.builder()
                    .aggregateType("Transaction")
                    .aggregateId(UUID.randomUUID())
                    .eventTopic("payment.processed")
                    .payload("{\"transactionId\":\"" + i + "\"}")
                    .status(OutboxEventEntity.EventStatus.PENDING)
                    .createdAt(OffsetDateTime.now())
                    .build();
            outboxEventRepository.save(event);
        }

        Pageable firstPage = PageRequest.of(0, 3);
        List<OutboxEventEntity> firstPageEvents = outboxEventRepository.findByStatus(
                OutboxEventEntity.EventStatus.PENDING, firstPage);

        assertThat(firstPageEvents).hasSize(3);

        Pageable secondPage = PageRequest.of(1, 3);
        List<OutboxEventEntity> secondPageEvents = outboxEventRepository.findByStatus(
                OutboxEventEntity.EventStatus.PENDING, secondPage);

        assertThat(secondPageEvents).hasSize(3);

        assertThat(firstPageEvents).extracting(OutboxEventEntity::getId)
                .doesNotContainAnyElementsOf(
                        secondPageEvents.stream().map(OutboxEventEntity::getId).toList());
    }

    @Test
    @DisplayName("Should return empty list when no events found with status")
    void shouldReturnEmptyListWhenNoEventsFoundWithStatus() {
        outboxEventRepository.deleteAll();
        outboxEventRepository.flush();

        Pageable pageable = PageRequest.of(0, 10);
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findByStatus(
                OutboxEventEntity.EventStatus.PENDING, pageable);

        assertThat(pendingEvents).isEmpty();
    }

    @Test
    @DisplayName("Should save and retrieve outbox event")
    void shouldSaveAndRetrieveOutboxEvent() {
        OutboxEventEntity newEvent = OutboxEventEntity.builder()
                .aggregateType("Transaction")
                .aggregateId(UUID.randomUUID())
                .eventTopic("payment.cancelled")
                .payload("{\"transactionId\":\"111\",\"reason\":\"cancelled\"}")
                .status(OutboxEventEntity.EventStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        OutboxEventEntity saved = outboxEventRepository.saveAndFlush(newEvent);
        OutboxEventEntity retrieved = outboxEventRepository.findById(saved.getId()).orElseThrow();

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo(saved.getId());
        assertThat(retrieved.getAggregateType()).isEqualTo("Transaction");
        assertThat(retrieved.getEventTopic()).isEqualTo("payment.cancelled");
        assertThat(retrieved.getPayload()).isEqualTo("{\"transactionId\":\"111\",\"reason\":\"cancelled\"}");
        assertThat(retrieved.getStatus()).isEqualTo(OutboxEventEntity.EventStatus.PENDING);
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should set default status to PENDING when not provided")
    void shouldSetDefaultStatusToPendingWhenNotProvided() {
        OutboxEventEntity eventWithoutStatus = OutboxEventEntity.builder()
                .aggregateType("Transaction")
                .aggregateId(UUID.randomUUID())
                .eventTopic("payment.processed")
                .payload("{\"transactionId\":\"222\"}")
                .build();

        OutboxEventEntity saved = outboxEventRepository.saveAndFlush(eventWithoutStatus);

        assertThat(saved.getStatus()).isEqualTo(OutboxEventEntity.EventStatus.PENDING);
    }

    @Test
    @DisplayName("Should set createdAt automatically when not provided")
    void shouldSetCreatedAtAutomaticallyWhenNotProvided() {
        OutboxEventEntity eventWithoutCreatedAt = OutboxEventEntity.builder()
                .aggregateType("Transaction")
                .aggregateId(UUID.randomUUID())
                .eventTopic("payment.processed")
                .payload("{\"transactionId\":\"333\"}")
                .status(OutboxEventEntity.EventStatus.PENDING)
                .build();

        OutboxEventEntity saved = outboxEventRepository.saveAndFlush(eventWithoutCreatedAt);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isBeforeOrEqualTo(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Should update event status")
    void shouldUpdateEventStatus() {
        pendingEvent1.setStatus(OutboxEventEntity.EventStatus.SENT);
        pendingEvent1.setProcessedAt(OffsetDateTime.now());
        OutboxEventEntity updated = outboxEventRepository.saveAndFlush(pendingEvent1);

        assertThat(updated.getStatus()).isEqualTo(OutboxEventEntity.EventStatus.SENT);
        assertThat(updated.getProcessedAt()).isNotNull();

        Pageable pageable = PageRequest.of(0, 10);
        List<OutboxEventEntity> pendingEvents = outboxEventRepository.findByStatus(
                OutboxEventEntity.EventStatus.PENDING, pageable);
        assertThat(pendingEvents).extracting(OutboxEventEntity::getId)
                .doesNotContain(pendingEvent1.getId());

        List<OutboxEventEntity> sentEvents = outboxEventRepository.findByStatus(
                OutboxEventEntity.EventStatus.SENT, pageable);
        assertThat(sentEvents).extracting(OutboxEventEntity::getId)
                .contains(pendingEvent1.getId());
    }

    @Test
    @DisplayName("Should delete outbox event")
    void shouldDeleteOutboxEvent() {
        UUID eventId = pendingEvent1.getId();
        outboxEventRepository.delete(pendingEvent1);
        outboxEventRepository.flush();

        Optional<OutboxEventEntity> deleted = outboxEventRepository.findById(eventId);
        assertThat(deleted).isEmpty();
    }

    @Test
    @DisplayName("Should find event by ID")
    void shouldFindEventById() {
        Optional<OutboxEventEntity> found = outboxEventRepository.findById(pendingEvent1.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(pendingEvent1.getId());
        assertThat(found.get().getAggregateType()).isEqualTo(pendingEvent1.getAggregateType());
        assertThat(found.get().getEventTopic()).isEqualTo(pendingEvent1.getEventTopic());
        assertThat(found.get().getPayload()).isEqualTo(pendingEvent1.getPayload());
        assertThat(found.get().getStatus()).isEqualTo(pendingEvent1.getStatus());
    }

    @Test
    @DisplayName("Should return empty when event not found by ID")
    void shouldReturnEmptyWhenEventNotFoundById() {
        Optional<OutboxEventEntity> found = outboxEventRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should save event with all fields")
    void shouldSaveEventWithAllFields() {
        UUID aggregateId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime processedAt = OffsetDateTime.now().plusMinutes(5);

        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateType("Account")
                .aggregateId(aggregateId)
                .eventTopic("account.created")
                .payload("{\"accountId\":\"" + aggregateId + "\"}")
                .status(OutboxEventEntity.EventStatus.SENT)
                .createdAt(createdAt)
                .processedAt(processedAt)
                .build();

        OutboxEventEntity saved = outboxEventRepository.saveAndFlush(event);
        OutboxEventEntity retrieved = outboxEventRepository.findById(saved.getId()).orElseThrow();

        assertThat(retrieved.getAggregateType()).isEqualTo("Account");
        assertThat(retrieved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(retrieved.getEventTopic()).isEqualTo("account.created");
        assertThat(retrieved.getPayload()).isEqualTo("{\"accountId\":\"" + aggregateId + "\"}");
        assertThat(retrieved.getStatus()).isEqualTo(OutboxEventEntity.EventStatus.SENT);
        assertThat(retrieved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(retrieved.getProcessedAt()).isEqualTo(processedAt);
    }
}
