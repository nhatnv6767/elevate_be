package com.elevatebanking.service.notification;

import com.elevatebanking.entity.notification.NotificationPreference;
import com.elevatebanking.entity.notification.NotificationTemplate;
import com.elevatebanking.event.EmailEvent;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.NotificationChannelRepository;
import com.elevatebanking.service.email.EmailEventService;
import com.elevatebanking.service.nonImp.EmailService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationDeliveryService {
    EmailService emailService;
    EmailEventService emailEventService;
    NotificationChannelRepository channelRepository;
    NotificationPreferenceService preferenceService;
    NotificationTemplateService templateService;

    @Transactional
    public void sendNotification(String userId, String templateCode, Map<String, Object> data) {
        try {


            if (isSystemAlert(templateCode)) {
                handleSystemAlert(templateCode, data);
                return;
            }

            if (userId == null) {
                log.warn("Attempted to send user notification with null userId");
                return;
            }

            NotificationPreference preferences = preferenceService.getUserPreferences(userId);
            NotificationTemplate template = templateService.getTemplate(templateCode);

            String content = templateService.renderTemplate(template, data);

            if (templateCode.equals("TRANSACTION_COMPLETED")) {
                if (preferences.isPushEnabled()) {
                    sendPushNotification(userId, template.getSubjectTemplate(), content);
                }

                if (preferences.isSmsEnabled()) {
                    sendSms(userId, content);
                }
                return;
            }

            if (preferences.isEmailEnabled()) {
                sendEmailSafely(userId, template.getSubjectTemplate(), content);
            }

            if (preferences.isPushEnabled()) {
                sendPushNotification(userId, template.getSubjectTemplate(), content);
            }

            if (preferences.isSmsEnabled()) {
                sendSms(userId, content);
            }

        } catch (Exception e) {
            log.error("Failed to send notification to user: {}", userId, e);
        }
    }

    private void handleSystemAlert(String templateCode, Map<String, Object> data) {
        try {
            NotificationTemplate template = templateService.getTemplate(templateCode);
            String content = templateService.renderTemplate(template, data);

            // Send to admin or monitoring channels
            sendSystemAlertEmail(template.getSubjectTemplate(), content);
//            sendSystemAlertPush(template.getSubjectTemplate(), content);

        } catch (Exception e) {
            log.error("Failed to send system alert: {}", e.getMessage());
        }
    }

    private void sendEmailSafely(String userId, String subject, String content) {
        try {
            if (userId != null) {
                emailService.sendTransactionEmail(userId, subject, content);
            } else {
                log.warn("Attempted to send email with null userId");
            }
        } catch (ResourceNotFoundException e) {
            log.warn("User not found for email notification: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send email to user: {}", userId, e);
        }
    }

    private void sendSystemAlertEmail(String subject, String content) {
        try {
            // Send to admin email configured in properties
            emailService.sendSystemAlert(subject, content);
        } catch (Exception e) {
            log.error("Failed to send system alert email: {}", e.getMessage());
        }
    }

    private boolean isSystemAlert(String templateCode) {
        return templateCode != null &&
                (templateCode.startsWith("SYSTEM_") ||
                        templateCode.equals("MONITORING_ALERT"));
    }

    void sendEmail(String userId, String subject, String content) {
        try {
            String deduplicationId = String.format("email:%s:%s:%s",
                    userId,
                    subject.replaceAll("\\s+", "_"),
                    LocalDateTime.now().toLocalDate());

            EmailEvent emailEvent = EmailEvent.createTransactionEmail(
                    userId,
                    subject,
                    content
            );
            
            emailEvent.setDeduplicationId(deduplicationId);

            emailEventService.sendEmailEvent(emailEvent);
            log.info("Email sent successfully for user: {}, deduplication key: {}", userId, deduplicationId);
        } catch (Exception e) {
            log.error("Failed to create email event for user: {}", userId, e);
            throw e;
        }
    }

    void sendPushNotification(String userId, String title, String content) {
        try {
            // TODO: Implement push notification logic
            log.info("Push notification sent successfully to user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send push notification to user: {}", userId, e);
        }
    }

    void sendSms(String userId, String content) {
        try {
            // TODO: Implement SMS sending logic
            log.info("SMS sent successfully to user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send SMS to user: {}", userId, e);
        }
    }
}
