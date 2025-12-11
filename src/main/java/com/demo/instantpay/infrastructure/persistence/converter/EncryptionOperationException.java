package com.demo.instantpay.infrastructure.persistence.converter;

public class EncryptionOperationException extends RuntimeException {

    public EncryptionOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
