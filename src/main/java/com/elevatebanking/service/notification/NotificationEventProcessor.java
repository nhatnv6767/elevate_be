package com.elevatebanking.service.notification;

import com.elevatebanking.entity.notification.NotificationPreference;
import com.elevatebanking.event.NotificationEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventProcessor {
    private final NotificationDeliveryService deliveryService;
    private final NotificationPreferenceService preferenceService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String MAIN_TOPIC = "${spring.kafka.topics.notification}";
    private static final String RETRY_TOPIC = "${spring.kafka.topics.notification-retry}";
    private static final String DLQ_TOPIC = "${spring.kafka.topics.notification-dlq}";

    @KafkaListener(
            topics = "${spring.kafka.topics.notification}",
            groupId = "${spring.kafka.consumer.groups.notification}",
            containerFactory = "notificationKafkaListenerContainerFactory"
    )
    public void processNotificationEvent(NotificationEvent event, Acknowledgment ack) {
        MDC.put("notificationId", event.getEventId());
        log.info("Processing notification event: {}", event);

        try {
            // get preferences based on user id
            NotificationPreference preferences = preferenceService.getUserPreferences(event.getUserId());

            // send notification based on preferences
            if (preferences.isEmailEnabled()) {
                deliveryService.sendEmail(event.getUserId(), event.getTitle(), event.getMessage());
            }

            if (preferences.isPushEnabled()) {
                deliveryService.sendPushNotification(event.getUserId(), event.getTitle(), event.getMessage());
            }

            if (preferences.isSmsEnabled()) {
                deliveryService.sendSms(event.getUserId(), event.getMessage());
            }

            ack.acknowledge();
            log.info("Successfully processed notification event: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Error processing notification event: {}", event, e);
            handleProcessingError(event, e, ack);
        } finally {
            MDC.remove("notificationId");
        }

    }

    @KafkaListener(
            topics = RETRY_TOPIC,
            groupId = "${spring.kafka.consumer.groups.notification-retry}",
            containerFactory = "notificationKafkaListenerContainerFactory"
    )

    public void processRetryEvent(NotificationEvent event, Acknowledgment ack) {
        log.info("Processing retry notification event: {}", event);
        processNotificationEvent(event, ack);
    }


    private void handleProcessingError(NotificationEvent event, Exception e, Acknowledgment ack) {
        int retryCount = event.getMetadata() != null ? (Integer) event.getMetadata().getOrDefault("retryCount", 0) : 0;
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            // increment retry count
            assert event.getMetadata() != null;
            event.getMetadata().put("retryCount", retryCount + 1);
            event.getMetadata().put("lastError", e.getMessage());
            event.getMetadata().put("retryTime", LocalDateTime.now().toString());

            kafkaTemplate.send(RETRY_TOPIC, event.getEventId(), event);
            log.info("Notification event {} sent to retry queue", event.getEventId());
        } else {
            kafkaTemplate.send(DLQ_TOPIC, event.getEventId(), event);
            log.error("Max retries reached for notification event {}. Sent to DLQ", event.getEventId());
        }
        ack.acknowledge();
    }
}
