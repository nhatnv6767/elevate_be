package com.elevatebanking.service.email;

import com.elevatebanking.event.EmailEvent;
import com.elevatebanking.service.nonImp.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    private static final String MAIN_TOPIC = "${spring.kafka.topics.email}";
    private static final String RETRY_TOPIC = "${spring.kafka.topics.email-retry}";
    private static final String DLQ_TOPIC = "${spring.kafka.topics.email-dlq}";

    @KafkaListener(topics = MAIN_TOPIC, groupId = "${spring.kafka.consumer.groups.email}", containerFactory = "emailKafkaListenerContainerFactory")
    public void processEmailEvent(EmailEvent event, Acknowledgment ack) {
        String emailId = UUID.randomUUID().toString();
        MDC.put("emailId", emailId);
        log.info("Processing email event: {}", event.getTo());
        try {
            Map<String, Object> templateData = event.getTemplateData();
            if (templateData == null) {
                log.error("Template data is null for email event: {}", event.getTo());
                return;
            }
            String token = (String) templateData.get("token");
            String username = (String) templateData.get("username");
            if (token == null || username == null) {
                log.error("Token or username is null for email event: {}", event.getTo());
                return;
            }
            emailService.sendResetPasswordEmail(event.getTo(), token, username);
            ack.acknowledge();
            log.info("Successfully processed email event: {}", event.getTo());
        } catch (Exception e) {
            log.error("Error processing email event: {}", event, e);
        } finally {
            MDC.remove("emailId");
        }
    }

    @KafkaListener(topics = RETRY_TOPIC, groupId = "${spring.kafka.consumer.groups.email-retry}", containerFactory = "emailKafkaListenerContainerFactory")
    public void processRetryEvent(EmailEvent event, Acknowledgment ack) {
        log.info("Processing retry email event for: {}", event.getTo());
        processEmailEvent(event, ack);
    }

    private void handleProcessingError(EmailEvent event, Exception e, Acknowledgment ack) {
        if (event.getMetadata() == null) {
            event.setTemplateData(new HashMap<>());
        }
        int retryCount = (Integer) event.getMetadata().getOrDefault("retryCount", 0);
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            // increment retry count
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
