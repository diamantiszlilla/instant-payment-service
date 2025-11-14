package com.alpian.instantpay.infrastructure.messaging;

import com.alpian.instantpay.infrastructure.exception.NotificationPublishException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaNotificationProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendNotification(String topic, String payload) {
        log.debug("Sending notification to topic: {}, payload: {}", topic, payload);

        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, payload);
            SendResult<String, String> result = future.get();

            log.info("Notification sent successfully to topic: {}, offset: {}",
                    topic, result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka send interrupted for topic: {}", topic, e);
            throw new NotificationPublishException("Interrupted while sending Kafka notification", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof KafkaException kafkaException) {
                log.error("Kafka exception while sending notification to topic: {}", topic, kafkaException);
                throw new NotificationPublishException("Kafka error while sending notification", kafkaException);
            }
            log.error("Unexpected error while sending notification to topic: {}", topic, cause);
            throw new NotificationPublishException("Unexpected error while sending Kafka notification", cause);
        } catch (KafkaException e) {
            log.error("Kafka exception while sending notification to topic: {}", topic, e);
            throw new NotificationPublishException("Kafka error while sending notification", e);
        }
    }
}
