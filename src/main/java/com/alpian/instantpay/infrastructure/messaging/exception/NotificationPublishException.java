package com.alpian.instantpay.infrastructure.messaging.exception;

/**
 * Exception thrown when the application fails to publish a notification event to Kafka.
 */
public class NotificationPublishException extends RuntimeException {

    public NotificationPublishException(String message) {
        super(message);
    }

    public NotificationPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}

