package com.alpian.instantpay.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaNotificationProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendNotification(String topic, String payload) {
        log.debug("Sending notification to topic: {}, payload: {}", topic, payload);

        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, payload);

            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Failed to send notification to topic: {}", topic, exception);
                    throw new RuntimeException("Failed to send Kafka message", exception);
                } else {
                    log.info("Notification sent successfully to topic: {}, offset: {}",
                            topic, result.getRecordMetadata().offset());
                }
            });
            future.join();
        } catch (Exception e) {
            log.error("Error sending notification to topic: {}", topic, e);
            throw new RuntimeException("Failed to send notification to Kafka", e);
        }
    }
}
