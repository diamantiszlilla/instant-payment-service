package com.alpian.instantpay.api;

import com.alpian.instantpay.api.dto.PaymentRequest;
import com.alpian.instantpay.api.dto.PaymentResponse;
import com.alpian.instantpay.config.SecurityConfig;
import com.alpian.instantpay.infrastructure.security.JwtAuthenticationFilter;
import com.alpian.instantpay.infrastructure.security.JwtTokenProvider;
import com.alpian.instantpay.service.PaymentService;
import com.alpian.instantpay.service.exception.AccountNotFoundException;
import com.alpian.instantpay.service.exception.IdempotencyException;
import com.alpian.instantpay.service.exception.InsufficientFundsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "jwt.secret=test-jwt-secret-key-must-be-at-least-32-characters-long-for-testing-purposes",
        "jwt.expirationMillis=3600000",
        "pii.encryption.key=VTgDasP1R776SZNpu+5p+KYyznjZUaGbzBO2Pfs7rAY="
})
@DisplayName("PaymentController Integration Tests")
class PaymentControllerTest {
    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private ObjectMapper objectMapper;

    private String validJwtToken;
    private PaymentRequest validRequest;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() {
        UserDetails userDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);

        validJwtToken = jwtTokenProvider.generateToken(userDetails);

        idempotencyKey = UUID.randomUUID();
        validRequest = new PaymentRequest(
                new BigDecimal("100.00"),
                "USD",
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when no JWT token provided")
    void shouldReturnUnauthorizedWhenNoToken() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when validation fails")
    void shouldReturnBadRequestWhenValidationFails() throws Exception {
        PaymentRequest invalidRequest = new PaymentRequest(
                new BigDecimal("-100.00"),
                "USD",
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validJwtToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when InsufficientFundsException is thrown")
    void shouldReturnBadRequestWhenInsufficientFunds() throws Exception {
        when(paymentService.sendMoney(any(PaymentRequest.class), any(UUID.class), any(String.class)))
                .thenThrow(new InsufficientFundsException("Insufficient funds"));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validJwtToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Should return 404 Not Found when AccountNotFoundException is thrown")
    void shouldReturnNotFoundWhenAccountNotFound() throws Exception {
        when(paymentService.sendMoney(any(PaymentRequest.class), any(UUID.class), any(String.class)))
                .thenThrow(new AccountNotFoundException("Account not found"));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validJwtToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Should return 409 Conflict when IdempotencyException is thrown")
    void shouldReturnConflictWhenIdempotencyViolation() throws Exception {
        when(paymentService.sendMoney(any(PaymentRequest.class), any(UUID.class), any(String.class)))
                .thenThrow(new IdempotencyException("Transaction already processed"));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validJwtToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_VIOLATION"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Should return 409 Conflict when OptimisticLockingFailureException is thrown")
    void shouldReturnConflictWhenOptimisticLockingFailure() throws Exception {
        when(paymentService.sendMoney(any(PaymentRequest.class), any(UUID.class), any(String.class)))
                .thenThrow(new OptimisticLockingFailureException("Concurrent modification"));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validJwtToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OPTIMISTIC_LOCKING_FAILURE"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("Should return 200 OK when payment is successful")
    @WithMockUser(username = "testuser")
    void shouldReturnOkWhenPaymentSuccessful() throws Exception {
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "USD",
                "COMPLETED",
                OffsetDateTime.now()
        );
        when(paymentService.sendMoney(any(PaymentRequest.class), eq(idempotencyKey), eq("testuser")))
                .thenReturn(response);

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validJwtToken)
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(100.00));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when Idempotency-Key header is missing")
    void shouldReturnBadRequestWhenIdempotencyKeyMissing() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + validJwtToken)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }
}
