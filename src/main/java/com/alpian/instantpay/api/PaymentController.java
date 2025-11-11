package com.alpian.instantpay.api;

import com.alpian.instantpay.api.dto.PaymentRequest;
import com.alpian.instantpay.api.dto.PaymentResponse;
import com.alpian.instantpay.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    private final PaymentService paymentService;

    @Operation(
            summary = "Process payment",
            description = """
                    Transfers money from authenticated user's account to recipient's account.
                    Idempotency-Key Header is required to avoid duplicate processing.
                    """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful payment"),
            @ApiResponse(responseCode = "400", description = "Invalid request (fund issue)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized (missing or invalid jwt)"),
            @ApiResponse(responseCode = "404", description = "No account found"),
            @ApiResponse(responseCode = "409", description = "Conflict (idempotency issue or locking failure)")
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> sendPayment(
            @Valid @RequestBody PaymentRequest request,
            @Parameter(description = "Unique idempotency key (UUID) to prevent duplicate processing",
                    required = true)
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            Authentication authentication) {

        String senderUsername = authentication.getName();

        log.info("Payment request received: sender={}, recipient={}, amount={}, idempotencyKey={}",
                senderUsername, request.recipientAccountId(), request.amount(), idempotencyKey);

        PaymentResponse paymentResponse = paymentService.sendMoney(request, idempotencyKey, senderUsername);

        return ResponseEntity.status(HttpStatus.OK).body(paymentResponse);
    }
}
