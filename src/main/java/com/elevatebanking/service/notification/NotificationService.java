package com.elevatebanking.service.notification;

import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.notification.NotificationPreference;
import com.elevatebanking.entity.notification.NotificationTemplate;
import com.elevatebanking.event.NotificationEvent;
import com.elevatebanking.event.TransactionEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationService {
    KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    NotificationDeliveryService deliveryService;
    NotificationTemplateService templateService;
    NotificationPreferenceService preferenceService;

    @Transactional
    public void sendTransactionNotification(TransactionEvent event) {
        try {
            // get user preferences based on transaction event
            String userId = getUserIdFromEvent(event);
            NotificationPreference preferences = preferenceService.getUserPreferences(userId);

            // generate notification content from template
            NotificationTemplate template = templateService.getTemplate(getTemplateCode(event));

            Map<String, Object> templateData = buildTemplateData(event);
            String content = templateService.renderTemplate(template, templateData);

            // create and send notification
            NotificationEvent notification = buildNotificationEvent(event, content);

            // send based on preferences
            if (preferences.isEmailEnabled()) {
                deliveryService.sendEmail(userId, notification.getTitle(), content);
            }

            if (preferences.isPushEnabled()) {
                deliveryService.sendPushNotification(userId, notification.getTitle(), content);
            }
            if (preferences.isSmsEnabled()) {
                deliveryService.sendSms(userId, content);
            }

            kafkaTemplate.send("elevate.notifications", notification.getEventId(), notification)
                    .whenComplete(((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send notification for transaction: {} - {}", event.getTransactionId(), ex.getMessage());
                        } else {
                            log.info("Successfully sent notification for transaction: {}", notification.getEventId());
                        }
                    }));
            ;
        } catch (Exception e) {
            log.error("Failed to process notification for transaction: {} - {}", event.getTransactionId(), e.getMessage());
        }
    }

    String getUserIdFromEvent(TransactionEvent event) {
        // for transfers, notify both parties
        if (event.getType() == TransactionType.TRANSFER) {
            return event.getFromAccount().getAccountId();// might want to notify both parties
        } else if (event.getType() == TransactionType.DEPOSIT) {
            return event.getToAccount().getAccountId();
        } else {
            return event.getFromAccount().getAccountId();
        }
    }

    String getTemplateCode(TransactionEvent event) {
        return event.getType().name() + "_" + event.getStatus().name();
    }

    Map<String, Object> buildTemplateData(TransactionEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", event.getTransactionId());
        data.put("amount", event.getAmount());
        data.put("type", event.getType());
        data.put("status", event.getStatus());

        if (event.getFromAccount() != null) {
            data.put("fromAccount", event.getFromAccount().getAccountNumber());
            data.put("fromAccountBalance", event.getFromAccount().getBalanceAfter());
        }
        if (event.getToAccount() != null) {
            data.put("toAccount", event.getToAccount().getAccountNumber());
            data.put("toAccountBalance", event.getToAccount().getBalanceAfter());
        }
        return data;
    }

    NotificationEvent buildNotificationEvent(TransactionEvent event, String content) {
        return NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(getUserIdFromEvent(event))
                .title(buildNotificationTitle(event))
                .message(content)
                .type(getNotificationType(event).name())
                .priority(getPriority(event).name())
                .transactionId(event.getTransactionId())
                .timestamp(LocalDateTime.now())
                .metadata(event.getMetadata())
                .build();
    }

    NotificationEvent.NotificationType getNotificationType(TransactionEvent event) {
        switch (event.getStatus()) {
            case COMPLETED:
                return NotificationEvent.NotificationType.TRANSACTION_COMPLETED;
            case FAILED:
                return NotificationEvent.NotificationType.TRANSACTION_FAILED;
            default:
                return NotificationEvent.NotificationType.TRANSACTION_INITIATED;
        }
    }

    NotificationEvent.Priority getPriority(TransactionEvent event) {
        if (event.getStatus() == TransactionStatus.FAILED) {
            return NotificationEvent.Priority.HIGH;
        }
        if (event.getErrorMessage() != null) {
            return NotificationEvent.Priority.HIGH;
        }
        // high priority for large transactions
        if (event.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            return NotificationEvent.Priority.MEDIUM;
        }
        return NotificationEvent.Priority.LOW;
    }

    String buildNotificationTitle(TransactionEvent event) {
        StringBuilder title = new StringBuilder();
        switch (event.getType()) {
            case TRANSFER:
                title.append("Money Transfer ");
                break;
            case DEPOSIT:
                title.append("Deposit ");
                break;
            case WITHDRAWAL:
                title.append("Withdrawal ");
                break;
        }
        switch (event.getStatus()) {
            case COMPLETED:
                title.append("Successful");
                break;
            case FAILED:
                title.append("Failed");
                break;
            case PENDING:
                title.append("Initiated");
                break;
            default:
                title.append(event.getStatus().name());
        }
        return title.toString();
    }
}
