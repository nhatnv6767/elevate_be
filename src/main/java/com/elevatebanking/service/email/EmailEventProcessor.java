package com.elevatebanking.service.email;

import com.elevatebanking.event.EmailEvent;
import com.elevatebanking.service.nonImp.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailEventProcessor {
    private final EmailService emailService;
    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;

    private static final int MAX_RETRY_ATTEMPTS = 3;

    private static final String MAIN_TOPIC = "elevate.emails";
    private static final String RETRY_TOPIC = "elevate.emails.retry";
    private static final String DLQ_TOPIC = "elevate.emails.dlq";

    @KafkaListener(topics = MAIN_TOPIC, groupId = "${spring.kafka.consumer.groups.email}", containerFactory = "emailKafkaListenerContainerFactory")
    public void processEmailEvent(EmailEvent event, Acknowledgment ack) {
        String emailId = UUID.randomUUID().toString();
        MDC.put("emailId", emailId);
        log.info("Processing email event: {}", event.getTo());
        try {

            if (event.getTemplateData() == null) {
                event.setTemplateData(new HashMap<>());
            }
            if (event.getContent() != null && event.getContent().contains("|")) {
                String[] parts = event.getContent().split("\\|");
                if (parts.length == 2) {
                    event.getTemplateData().put("username", parts[0].trim());
                    event.getTemplateData().put("token", parts[1].trim());
                }
            }

            if (!validateTemplateData(event)) {
                log.error("Template data is null for email event: {}", event.getTo());
                handleProcessingError(event, new RuntimeException("Template data is null"), ack);
                return;
            }
            String token = (String) event.getTemplateData().get("token");
            String username = (String) event.getTemplateData().get("username");
            emailService.sendResetPasswordEmail(event.getTo(), token, username);
            ack.acknowledge();
            log.info("Successfully processed email event: {}", event.getTo());
        } catch (Exception e) {
            log.error("Error processing email event: {}", event, e);
            handleProcessingError(event, e, ack);
        } finally {
            MDC.remove("emailId");
        }
    }

    private boolean validateTemplateData(EmailEvent event) {
        Map<String, Object> templateData = event.getTemplateData();
        return templateData != null &&
                templateData.containsKey("username") &&
                templateData.containsKey("token") &&
                templateData.get("username") != null &&
                templateData.get("token") != null;
        
    }

    @KafkaListener(topics = RETRY_TOPIC, groupId = "${spring.kafka.consumer.groups.email-retry}", containerFactory = "emailKafkaListenerContainerFactory")
    public void processRetryEvent(EmailEvent event, Acknowledgment ack) {
        log.info("Processing retry email event for: {}", event.getTo());
        processEmailEvent(event, ack);
    }

    private void handleProcessingError(EmailEvent event, Exception e, Acknowledgment ack) {
        int retryCount = event.getMetadata() != null ? (Integer) event.getMetadata().getOrDefault("retryCount", 0) : 0;
        if (retryCount < MAX_RETRY_ATTEMPTS) {

            if (event.getMetadata() == null) {
                event.setMetadata(new HashMap<>());
            }

            event.getMetadata().put("retryCount", retryCount + 1);
            event.getMetadata().put("lastError", e.getMessage());
            event.getMetadata().put("retryTime", LocalDateTime.now().toString());

            kafkaTemplate.send(RETRY_TOPIC, event.getTo(), event);
            log.info("Email event for {} sent to retry queue. Attempt: {}", event.getTo(), retryCount + 1);
        } else {
            kafkaTemplate.send(DLQ_TOPIC, event.getTo(), event);
            log.error("Max retries reached for email to {}. Sent to DLQ", event.getTo());
        }
        ack.acknowledge();
    }
}
