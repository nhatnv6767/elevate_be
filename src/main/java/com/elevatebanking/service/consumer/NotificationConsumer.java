package com.elevatebanking.service.consumer;

import com.elevatebanking.entity.Notification;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.event.NotificationEvent;
import com.elevatebanking.repository.NotificationRepository;
import com.elevatebanking.repository.UserRepository;
import com.elevatebanking.service.nonImp.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public void handleNotification(NotificationEvent event, Acknowledgment ack) {
        log.info("Received notification event: {}", event);
        try {
            User user = userRepository.findById(event.getEventId()).orElseThrow(() -> new RuntimeException("User not found"));

            // luu notification vao db
            Notification notification = createNotification(event, user);
            notification = notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Failed to process notification event: {} - {}", event, e.getMessage());
            handleProcessingError(event, e, ack);
        }
    }


    private Notification createNotification(NotificationEvent event, User user) {
        return Notification.builder()
                .user(user)
                .title(event.getTitle())
                .message(event.getMessage())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void sendNotificationAsync(Notification notification, User user) {
        // su dung completableFuture de gui notification bat dong bo
        CompletableFuture.runAsync(() -> {
            try {
                sendEmailNotification(notification, user);

                // can add
                // PUST notification
                //
                // SMS
                // In app notification
            } catch (Exception e) {
                log.error("Failed to send notification: {}", e.getMessage());
            }
        });
    }

    private void sendEmailNotification(Notification notification, User user) {
        try {
            String emailContent = buildEmailContent(notification);
            emailService.sendTransactionEmail(user.getEmail(), notification.getTitle(), emailContent);
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage());
        }
    }

    private String buildEmailContent(Notification notification) {
        StringBuilder content = new StringBuilder();
        content.append("<div style='font-family: Arial, sans-serif; padding: 20px;'>");
        content.append("<h2>").append(notification.getTitle()).append("</h2>");
        content.append("<p>").append(notification.getMessage()).append("</p>");
        content.append("<hr>");
        content.append("<p style='color: #666; font-size: 12px'>");
        content.append("This is an automated message from Elevate Banking. Please do not reply.");
        content.append("</p>");
        content.append("</div>");
        return content.toString();
    }

    private void handleProcessingError(NotificationEvent event, Exception e, Acknowledgment ack) {
        log.error("Failed to process notification event: {}", event, e);

        // luu failed notification vao database voi trang thai failed
        try {
            User user = userRepository.findById(event.getUserId()).get();
            Notification failedNotification = createNotification(event, user);
            failedNotification.setStatus("FAILED");
            failedNotification.setErrorMessage(e.getMessage());
            notificationRepository.save(failedNotification);
        } catch (Exception ex) {
            log.error("Failed to save failed notification: {}", ex.getMessage());
        }

        // acknowledge message de tranh reprocessing
        ack.acknowledge();

    }
}
