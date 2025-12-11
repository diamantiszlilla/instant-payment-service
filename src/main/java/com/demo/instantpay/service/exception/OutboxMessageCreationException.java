package com.demo.instantpay.service.exception;

public class OutboxMessageCreationException extends RuntimeException {

    public OutboxMessageCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
