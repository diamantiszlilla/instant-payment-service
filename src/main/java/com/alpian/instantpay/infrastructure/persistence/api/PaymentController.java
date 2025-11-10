package com.alpian.instantpay.infrastructure.persistence.api;

import com.alpian.instantpay.infrastructure.persistence.api.dto.PaymentRequest;
import com.alpian.instantpay.infrastructure.persistence.api.dto.PaymentResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Payment processing API")
public class PaymentController {

    @PostMapping
    public ResponseEntity<PaymentResponse> sendPayment(
            @Valid @RequestBody PaymentRequest request,
            @Parameter(description = "Unique idempotency key (UUID) to prevent duplicate processing",
                    required = true)
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            Authentication authentication) {

        String senderUsername = authentication.getName();

        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
