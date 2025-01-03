package com.elevatebanking.service.notification;

import com.elevatebanking.entity.notification.NotificationPreference;
import com.elevatebanking.entity.notification.NotificationTemplate;
import com.elevatebanking.repository.NotificationChannelRepository;
import com.elevatebanking.service.nonImp.EmailService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationDeliveryService {
    EmailService emailService;
    NotificationChannelRepository channelRepository;
    NotificationPreferenceService preferenceService;
    NotificationTemplateService templateService;

    @Transactional
    public void sendNotification(String userId, String templateCode, Map<String, Object> data) {
        NotificationPreference preferences = preferenceService.getUserPreferences(userId);
        NotificationTemplate template = templateService.getTemplate(templateCode);

        String content = templateService.renderTemplate(template, data);

        if (preferences.isEmailEnabled()) {
            sendEmail(userId, template.getSubjectTemplate(), content);
        }

        if (preferences.isPushEnabled()) {
            sendPushNotification(userId, template.getSubjectTemplate(), content);
        }

        if (preferences.isSmsEnabled()) {
            sendSms(userId, content);
        }
    }

    void sendEmail(String userId, String subject, String content) {
        try {
            emailService.sendTransactionEmail(userId, subject, content);
            // TODO: Implement the email service
//            emailService.sendEmail(userId, subject, content);
        } catch (Exception e) {
            log.error("Failed to send email to user: {}", userId, e);
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
