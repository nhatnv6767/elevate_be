package com.elevatebanking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class EmailEvent {
    private String eventId;
    private String deduplicationId;
    private String to;
    private String subject;
    private String content;
    private EmailType type;
    private Map<String, Object> templateData;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
    private List<String> processSteps;

    public Map<String, Object> getMetadata() {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        return metadata;
    }

    public void addProcessStep(String step) {
        if (processSteps == null) {
            processSteps = new ArrayList<>();
        }
        processSteps.add(step + " at " + LocalDateTime.now());
    }

    private void validateTemplateData() {
        if (templateData == null) {
            templateData = new HashMap<>();
        }
        Set<String> requiredFields = type.getRequiredTemplateFields();
        Set<String> missingFields = new HashSet<>();

        for (String field : requiredFields) {
            if (!templateData.containsKey(field) || templateData.get(field) == null) {
                missingFields.add(field);
                log.warn("Missing required template field for {}: {}", type, field);
            }
        }

        if (!missingFields.isEmpty()) {
            String error = String.format("Missing required template fields for %s: %s", type, missingFields);
            log.error(error);
            throw new IllegalArgumentException(error);
        }

    }

    public void validate() {
        if (type == null) {
            throw new IllegalArgumentException("Email type cannot be null");
        }
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient email cannot be null or empty");
        }
        if (deduplicationId == null || deduplicationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Deduplication ID must be set");
        }
        validateTemplateData();
        addProcessStep("VALIDATED");
    }

    private static class CustomEmailEventBuilder extends EmailEventBuilder {
        @Override
        public EmailEvent build() {
            if (super.eventId == null) {
                super.eventId = UUID.randomUUID().toString();
            }
            if (super.timestamp == null) {
                super.timestamp = LocalDateTime.now();
            }
            EmailEvent event = super.build();
            event.addProcessStep("CREATED");
            return event;
        }
    }

    public static EmailEventBuilder builder() {
        return new CustomEmailEventBuilder();
    }


    public static EmailEvent createPasswordResetEmail(String email, String username, String token) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("username", username);
        templateData.put("token", token);

        return builder()
                .to(email)
                .type(EmailType.PASSWORD_RESET)
                .subject(EmailType.PASSWORD_RESET.getDefaultSubject())
                .templateData(templateData)
                .build();
    }

    public static EmailEvent createTransactionEmail(String userId, String subject, String message) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("subject", subject);
        templateData.put("message", message);
        templateData.put("bankName", "Elevate Banking");
        templateData.put("supportEmail", "support@elevatebanking.com");
        return builder()
                .to(userId)
                .type(EmailType.TRANSACTION)
                .subject(subject)
                .content(message)
                .templateData(templateData).build();
    }

    public String debugInfo() {
        return String.format("EmailEvent[id=%s, type=%s, to=%s, subject=%s, templateData=%s]",
                eventId, type, to, subject, templateData);
    }
}