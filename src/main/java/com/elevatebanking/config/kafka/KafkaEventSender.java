package com.elevatebanking.config.kafka;

import com.elevatebanking.entity.log.FailedEvent;
import com.elevatebanking.repository.FailedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaEventSender {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RetryTemplate retryTemplate;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    public void sendWithRetry(String topic, String key, Object event) {
        try {
            retryTemplate.execute(context -> {
                return kafkaTemplate.send(topic, key, event)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Error sending event to Kafka - Topic: {}, Key: {}, Attempt: {}", topic, key, context.getRetryCount(), ex);
                                throw new KafkaException("Error when sending event to Kafka", ex);
                            } else {
                                log.info("Sent event successfully - Topic: {}, Key: {}, Partition: {}",
                                        topic, key, result.getRecordMetadata().partition());
                            }
                        });
            });
        } catch (Exception e) {
            log.error("Error sending event to Kafka - Topic: {}, Key: {}", topic, key, e);
            handleFailedEvent(topic, key, event);
        }
    }

    private void handleFailedEvent(String topic, String key, Object event) {
        try {
            FailedEvent failedEvent = FailedEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .topic(topic)
                    .key(key)
                    .payload(objectMapper.writeValueAsString(event))
                    .failedAt(LocalDateTime.now())
                    .status("FAILED")
                    .build();

            failedEventRepository.save(failedEvent);
            log.info("Saved failed event - Topic: {}, Key: {}", topic, key);
            log.info("Failed event to database - ID: {}", failedEvent.getId());

        } catch (Exception e) {
            log.error("Error handling failed event - Topic: {}, Key: {}", topic, key, e);
            throw new KafkaException("Error when handling failed event", e);
        }
    }
}
