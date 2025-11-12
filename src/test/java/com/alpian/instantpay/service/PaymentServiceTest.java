package com.alpian.instantpay.service;

import com.alpian.instantpay.api.dto.PaymentRequest;
import com.alpian.instantpay.api.dto.PaymentResponse;
import com.alpian.instantpay.infrastructure.persistence.entity.AccountEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.TransactionEntity;
import com.alpian.instantpay.infrastructure.persistence.entity.UserEntity;
import com.alpian.instantpay.infrastructure.persistence.repository.AccountRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.OutboxEventRepository;
import com.alpian.instantpay.infrastructure.persistence.repository.TransactionRepository;
import com.alpian.instantpay.service.exception.AccountNotFoundException;
import com.alpian.instantpay.service.exception.IdempotencyException;
import com.alpian.instantpay.service.exception.InsufficientFundsException;
import com.alpian.instantpay.service.mapper.PaymentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private ObjectMapper objectMapper;
    @InjectMocks
    private PaymentService paymentService;

    private UserEntity senderUser;
    private AccountEntity senderAccount;
    private AccountEntity recipientAccount;
    private PaymentRequest paymentRequest;
    private UUID idempotencyKey;
    private UUID senderAccountId;
    private UUID recipientAccountId;

    @BeforeEach
    void setUp() {
        idempotencyKey = UUID.randomUUID();
        senderAccountId = UUID.randomUUID();
        recipientAccountId = UUID.randomUUID();

        senderUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .username("sender")
                .passwordHash("$2a$10$hashed")
                .role("USER")
                .createdAt(OffsetDateTime.now())
                .build();

        senderAccount = AccountEntity.builder()
                .id(senderAccountId)
                .user(senderUser)
                .accountNumber("1111111111")
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        recipientAccount = AccountEntity.builder()
                .id(recipientAccountId)
                .user(UserEntity.builder()
                        .id(UUID.randomUUID())
                        .username("recipient")
                        .passwordHash("$2a$10$hashed")
                        .role("USER")
                        .createdAt(OffsetDateTime.now())
                        .build())
                .accountNumber("2222222222")
                .balance(new BigDecimal("500.00"))
                .currency("USD")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        paymentRequest = new PaymentRequest(
                new BigDecimal("100.00"),
                "USD",
                senderAccountId,
                recipientAccountId
        );
    }

    @Test
    @DisplayName("Should successfully process payment and create outbox event")
    void shouldSuccessfullyProcessPayment() throws Exception {
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(senderAccountId))
                .thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(recipientAccountId))
                .thenReturn(Optional.of(recipientAccount));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"transactionId\":\"test\"}");
        when(paymentMapper.toPaymentResponse(any()))
                .thenReturn(new PaymentResponse(
                        UUID.randomUUID(),
                        senderAccountId,
                        recipientAccountId,
                        new BigDecimal("100.00"),
                        "USD",
                        "COMPLETED",
                        OffsetDateTime.now()
                ));

        PaymentResponse response = paymentService.sendMoney(
                paymentRequest, idempotencyKey, "sender"
        );

        assertThat(response).isNotNull();

        ArgumentCaptor<AccountEntity> accountCaptor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository, times(2)).save(accountCaptor.capture());

        List<AccountEntity> savedAccounts = accountCaptor.getAllValues();
        AccountEntity savedSender = savedAccounts.stream()
                .filter(acc -> acc.getId().equals(senderAccountId))
                .findFirst()
                .orElseThrow();
        AccountEntity savedRecipient = savedAccounts.stream()
                .filter(acc -> acc.getId().equals(recipientAccountId))
                .findFirst()
                .orElseThrow();

        assertThat(savedSender.getBalance()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(savedRecipient.getBalance()).isEqualByComparingTo(new BigDecimal("600.00"));

        ArgumentCaptor<TransactionEntity> transactionCaptor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        TransactionEntity savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(savedTransaction.getStatus()).isEqualTo(TransactionEntity.TransactionStatus.COMPLETED);

        ArgumentCaptor<com.alpian.instantpay.infrastructure.persistence.entity.OutboxEventEntity> outboxCaptor =
                ArgumentCaptor.forClass(com.alpian.instantpay.infrastructure.persistence.entity.OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        com.alpian.instantpay.infrastructure.persistence.entity.OutboxEventEntity outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getStatus()).isEqualTo(
                com.alpian.instantpay.infrastructure.persistence.entity.OutboxEventEntity.EventStatus.PENDING
        );
        assertThat(outboxEvent.getEventTopic()).isEqualTo("payment.completed");
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException when balance is too low")
    void shouldThrowInsufficientFundsException() {
        senderAccount.setBalance(new BigDecimal("50.00"));
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(senderAccountId))
                .thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(recipientAccountId))
                .thenReturn(Optional.of(recipientAccount));

        assertThatThrownBy(() -> paymentService.sendMoney(paymentRequest, idempotencyKey, "sender"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when sender account not found")
    void shouldThrowAccountNotFoundExceptionWhenSenderAccountNotFound() {
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(senderAccountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.sendMoney(paymentRequest, idempotencyKey, "sender"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Sender account not found");

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when recipient account not found")
    void shouldThrowAccountNotFoundExceptionWhenRecipientAccountNotFound() {
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(senderAccountId))
                .thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(recipientAccountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.sendMoney(paymentRequest, idempotencyKey, "sender"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Recipient account not found");

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IdempotencyException when idempotency key already exists")
    void shouldThrowIdempotencyException() {
        TransactionEntity existingTransaction = TransactionEntity.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .status(TransactionEntity.TransactionStatus.COMPLETED)
                .build();
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingTransaction));

        assertThatThrownBy(() -> paymentService.sendMoney(paymentRequest, idempotencyKey, "sender"))
                .isInstanceOf(IdempotencyException.class)
                .hasMessageContaining("already processed");

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when currency mismatch with sender")
    void shouldThrowExceptionWhenCurrencyMismatchWithSender() {
        senderAccount.setCurrency("EUR");

        var recipientAccount = new AccountEntity();
        recipientAccount.setId(paymentRequest.recipientAccountId());
        recipientAccount.setCurrency(paymentRequest.currency());

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(senderAccountId))
                .thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(paymentRequest.recipientAccountId()))
                .thenReturn(Optional.of(recipientAccount));

        assertThatThrownBy(() -> paymentService.sendMoney(paymentRequest, idempotencyKey, "sender"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    @DisplayName("Should throw exception when currency mismatch with recipient")
    void shouldThrowExceptionWhenCurrencyMismatchWithRecipient() {
        recipientAccount.setCurrency("EUR");
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(senderAccountId))
                .thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdForUpdate(recipientAccountId))
                .thenReturn(Optional.of(recipientAccount));

        assertThatThrownBy(() -> paymentService.sendMoney(paymentRequest, idempotencyKey, "sender"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    @DisplayName("Should throw exception when trying to transfer to same account")
    void shouldThrowExceptionWhenTransferringToSameAccount() {
        paymentRequest = new PaymentRequest(
                new BigDecimal("100.00"),
                "USD",
                senderAccountId,
                senderAccountId
        );
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(senderAccountId))
                .thenReturn(Optional.of(senderAccount));

        assertThatThrownBy(() -> paymentService.sendMoney(paymentRequest, idempotencyKey, "sender"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same account");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user attempts to use account they don't own")
    void shouldThrowAccessDeniedExceptionWhenUserDoesNotOwnAccount() {
        UserEntity otherUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .username("otheruser")
                .passwordHash("$2a$10$hashed")
                .role("USER")
                .createdAt(OffsetDateTime.now())
                .build();

        AccountEntity otherUserAccount = AccountEntity.builder()
                .id(senderAccountId)
                .user(otherUser)
                .accountNumber("9999999999")
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .version(0L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(senderAccountId))
                .thenReturn(Optional.of(otherUserAccount));

        assertThatThrownBy(() -> paymentService.sendMoney(paymentRequest, idempotencyKey, "sender"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("User does not own this account");

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
