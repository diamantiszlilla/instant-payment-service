package com.alpian.instantpay.infrastructure.exception;

public class NotificationPublishException extends RuntimeException {

    public NotificationPublishException(String message) {
        super(message);
    }

    public NotificationPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
