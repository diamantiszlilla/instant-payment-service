package com.alpian.instantpay.infrastructure.persistence.repository;

import com.alpian.instantpay.AbstractDbIntegrationTest;
import com.alpian.instantpay.infrastructure.persistence.entity.AccountEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.TransactionEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.UserEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("TransactionRepository Tests")
@EnableJpaRepositories(basePackages = "com.alpian.instantpay.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.alpian.instantpay.infrastructure.persistence.entity")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=true"
})
class TransactionRepositoryTest extends AbstractDbIntegrationTest {

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
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;

    private UserEntity senderUser;
    private UserEntity recipientUser;
    private AccountEntity senderAccount;
    private AccountEntity recipientAccount;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() {
        senderUser = UserEntity.builder()
                .username("sender")
                .passwordHash("$2a$10$hashed")
                .role("USER")
                .createdAt(OffsetDateTime.now())
                .build();
        senderUser = userRepository.save(senderUser);

        senderAccount = AccountEntity.builder()
                .user(senderUser)
                .accountNumber("1111111111")
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        senderAccount = accountRepository.save(senderAccount);

        recipientUser = UserEntity.builder()
                .username("recipient")
                .passwordHash("$2a$10$hashed")
                .role("USER")
                .createdAt(OffsetDateTime.now())
                .build();
        recipientUser = userRepository.save(recipientUser);

        recipientAccount = AccountEntity.builder()
                .user(recipientUser)
                .accountNumber("2222222222")
                .balance(new BigDecimal("500.00"))
                .currency("USD")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        recipientAccount = accountRepository.save(recipientAccount);

        idempotencyKey = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should enforce UNIQUE constraint on idempotency_key")
    void shouldRejectDuplicateIdempotencyKey() {
        TransactionEntity transaction1 = TransactionEntity.builder()
                .senderAccount(senderAccount)
                .recipientAccount(recipientAccount)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(TransactionEntity.TransactionStatus.COMPLETED)
                .idempotencyKey(idempotencyKey)
                .createdAt(OffsetDateTime.now())
                .build();
        transactionRepository.saveAndFlush(transaction1);

        TransactionEntity transaction2 = TransactionEntity.builder()
                .senderAccount(senderAccount)
                .recipientAccount(recipientAccount)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .status(TransactionEntity.TransactionStatus.COMPLETED)
                .idempotencyKey(idempotencyKey)
                .createdAt(OffsetDateTime.now())
                .build();

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(transaction2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idempotency_key");
    }

    @Test
    @DisplayName("Should allow different idempotency keys")
    void shouldAllowDifferentIdempotencyKeys() {
        UUID key1 = UUID.randomUUID();
        UUID key2 = UUID.randomUUID();

        TransactionEntity transaction1 = TransactionEntity.builder()
                .senderAccount(senderAccount)
                .recipientAccount(recipientAccount)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(TransactionEntity.TransactionStatus.COMPLETED)
                .idempotencyKey(key1)
                .createdAt(OffsetDateTime.now())
                .build();

        TransactionEntity transaction2 = TransactionEntity.builder()
                .senderAccount(senderAccount)
                .recipientAccount(recipientAccount)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .status(TransactionEntity.TransactionStatus.COMPLETED)
                .idempotencyKey(key2)
                .createdAt(OffsetDateTime.now())
                .build();

        TransactionEntity saved1 = transactionRepository.saveAndFlush(transaction1);
        TransactionEntity saved2 = transactionRepository.saveAndFlush(transaction2);

        assertThat(saved1).isNotNull();
        assertThat(saved2).isNotNull();
        assertThat(saved1.getIdempotencyKey()).isEqualTo(key1);
        assertThat(saved2.getIdempotencyKey()).isEqualTo(key2);
    }

    @Test
    @DisplayName("Should find transaction by idempotency key")
    void shouldFindTransactionByIdempotencyKey() {
        TransactionEntity transaction = TransactionEntity.builder()
                .senderAccount(senderAccount)
                .recipientAccount(recipientAccount)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(TransactionEntity.TransactionStatus.COMPLETED)
                .idempotencyKey(idempotencyKey)
                .createdAt(OffsetDateTime.now())
                .build();
        transactionRepository.saveAndFlush(transaction);

        Optional<TransactionEntity> found = transactionRepository.findByIdempotencyKey(idempotencyKey);

        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(found.get().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should return empty when idempotency key not found")
    void shouldReturnEmptyWhenIdempotencyKeyNotFound() {
        Optional<TransactionEntity> found = transactionRepository.findByIdempotencyKey(UUID.randomUUID());

        assertThat(found).isEmpty();
    }
}
