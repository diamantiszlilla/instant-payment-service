package com.alpian.instantpay.infrastructure.persistence.repository;

import com.alpian.instantpay.AbstractDbIntegrationTest;
import com.alpian.instantpay.infrastructure.persistence.entity.AccountEntity;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("AccountRepository Tests")
@EnableJpaRepositories(basePackages = "com.alpian.instantpay.infrastructure.persistence.repository")
@EntityScan(basePackages = "com.alpian.instantpay.infrastructure.persistence.entity")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=true"
})
class AccountRepositoryTest extends AbstractDbIntegrationTest {

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
    private AccountRepository accountRepository;
    @Autowired
    private UserRepository userRepository;

    private UserEntity user1;
    private UserEntity user2;
    private AccountEntity account1;
    private AccountEntity account2;
    private AccountEntity account3;

    @BeforeEach
    void setUp() {
        user1 = UserEntity.builder()
                .username("user1")
                .passwordHash("$2a$10$hashed")
                .role("USER")
                .createdAt(OffsetDateTime.now())
                .build();
        user1 = userRepository.save(user1);

        user2 = UserEntity.builder()
                .username("user2")
                .passwordHash("$2a$10$hashed")
                .role("USER")
                .createdAt(OffsetDateTime.now())
                .build();
        user2 = userRepository.save(user2);

        account1 = AccountEntity.builder()
                .user(user1)
                .accountNumber("1111111111")
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        account1 = accountRepository.save(account1);

        account2 = AccountEntity.builder()
                .user(user1)
                .accountNumber("2222222222")
                .balance(new BigDecimal("500.00"))
                .currency("EUR")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        account2 = accountRepository.save(account2);

        account3 = AccountEntity.builder()
                .user(user2)
                .accountNumber("3333333333")
                .balance(new BigDecimal("2000.00"))
                .currency("USD")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        account3 = accountRepository.save(account3);
    }

    @Test
    @DisplayName("Should find accounts by user ID")
    void shouldFindAccountsByUserId() {
        List<AccountEntity> user1Accounts = accountRepository.findByUserId(user1.getId());
        List<AccountEntity> user2Accounts = accountRepository.findByUserId(user2.getId());

        assertThat(user1Accounts).hasSize(2);
        assertThat(user1Accounts).extracting(AccountEntity::getId)
                .containsExactlyInAnyOrder(account1.getId(), account2.getId());
        assertThat(user1Accounts).extracting(AccountEntity::getAccountNumber)
                .containsExactlyInAnyOrder("1111111111", "2222222222");

        assertThat(user2Accounts).hasSize(1);
        assertThat(user2Accounts).extracting(AccountEntity::getId)
                .containsExactly(account3.getId());
        assertThat(user2Accounts).extracting(AccountEntity::getAccountNumber)
                .containsExactly("3333333333");
    }

    @Test
    @DisplayName("Should find all accounts by user ID")
    void shouldFindAllAccountsByUserId() {
        List<AccountEntity> user1Accounts = accountRepository.findAllByUserId(user1.getId());

        assertThat(user1Accounts).hasSize(2);
        assertThat(user1Accounts).extracting(AccountEntity::getId)
                .containsExactlyInAnyOrder(account1.getId(), account2.getId());
    }

    @Test
    @DisplayName("Should return empty list when user has no accounts")
    void shouldReturnEmptyListWhenUserHasNoAccounts() {
        UserEntity newUser = UserEntity.builder()
                .username("newuser")
                .passwordHash("$2a$10$hashed")
                .role("USER")
                .createdAt(OffsetDateTime.now())
                .build();
        newUser = userRepository.save(newUser);

        List<AccountEntity> accounts = accountRepository.findByUserId(newUser.getId());

        assertThat(accounts).isEmpty();
    }

    @Test
    @DisplayName("Should find account by ID for update with pessimistic lock")
    void shouldFindAccountByIdForUpdate() {
        Optional<AccountEntity> found = accountRepository.findByIdForUpdate(account1.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(account1.getId());
        assertThat(found.get().getAccountNumber()).isEqualTo("1111111111");
        assertThat(found.get().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(found.get().getCurrency()).isEqualTo("USD");
        assertThat(found.get().getUser().getId()).isEqualTo(user1.getId());
    }

    @Test
    @DisplayName("Should return empty when account not found for update")
    void shouldReturnEmptyWhenAccountNotFoundForUpdate() {
        Optional<AccountEntity> found = accountRepository.findByIdForUpdate(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should save and retrieve account with encrypted account number")
    void shouldSaveAndRetrieveAccountWithEncryptedAccountNumber() {
        AccountEntity newAccount = AccountEntity.builder()
                .user(user1)
                .accountNumber("9999999999")
                .balance(new BigDecimal("750.00"))
                .currency("GBP")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        AccountEntity saved = accountRepository.saveAndFlush(newAccount);
        AccountEntity retrieved = accountRepository.findById(saved.getId()).orElseThrow();

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getAccountNumber()).isEqualTo("9999999999");
        assertThat(retrieved.getBalance()).isEqualByComparingTo(new BigDecimal("750.00"));
        assertThat(retrieved.getCurrency()).isEqualTo("GBP");
        assertThat(retrieved.getUser().getId()).isEqualTo(user1.getId());
    }

    @Test
    @DisplayName("Should enforce foreign key constraint on user_id")
    void shouldEnforceForeignKeyConstraintOnUserId() {
        AccountEntity accountWithInvalidUser = AccountEntity.builder()
                .user(UserEntity.builder()
                        .id(UUID.randomUUID())
                        .username("nonexistent")
                        .passwordHash("hash")
                        .role("USER")
                        .createdAt(OffsetDateTime.now())
                        .build())
                .accountNumber("4444444444")
                .balance(new BigDecimal("100.00"))
                .currency("USD")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        assertThatThrownBy(() -> accountRepository.saveAndFlush(accountWithInvalidUser))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("user_id");
    }

    @Test
    @DisplayName("Should update account balance")
    void shouldUpdateAccountBalance() {
        Long originalVersion = account1.getVersion();
        account1.setBalance(new BigDecimal("1500.00"));
        AccountEntity updated = accountRepository.saveAndFlush(account1);

        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(updated.getVersion()).isGreaterThan(originalVersion);
    }

    @Test
    @DisplayName("Should delete account")
    void shouldDeleteAccount() {
        UUID accountId = account1.getId();
        accountRepository.delete(account1);
        accountRepository.flush();

        Optional<AccountEntity> deleted = accountRepository.findById(accountId);
        assertThat(deleted).isEmpty();
    }

    @Test
    @DisplayName("Should maintain account version for optimistic locking")
    void shouldMaintainAccountVersionForOptimisticLocking() {
        Long initialVersion = account1.getVersion();
        
        account1.setBalance(new BigDecimal("1200.00"));
        accountRepository.saveAndFlush(account1);
        accountRepository.flush();
        
        AccountEntity reloaded1 = accountRepository.findById(account1.getId()).orElseThrow();
        assertThat(reloaded1.getVersion()).isGreaterThan(initialVersion);

        Long secondVersion = reloaded1.getVersion();
        reloaded1.setBalance(new BigDecimal("1300.00"));
        accountRepository.saveAndFlush(reloaded1);
        accountRepository.flush();
        
        AccountEntity reloaded2 = accountRepository.findById(account1.getId()).orElseThrow();
        assertThat(reloaded2.getVersion()).isGreaterThan(secondVersion);
    }
}
