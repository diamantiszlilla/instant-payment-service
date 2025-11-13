package com.alpian.instantpay.service.exception;

public class OutboxMessageCreationException extends RuntimeException {

    public OutboxMessageCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
