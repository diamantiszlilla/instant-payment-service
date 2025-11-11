package com.alpian.instantpay.api.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String message,
        String error,
        int status,
        OffsetDateTime timestamp,
        String path
) {
    public ErrorResponse(String message, String error, int status, String path) {
        this(message, error, status, OffsetDateTime.now(), path);
    }
}

