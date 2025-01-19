package com.elevatebanking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor // Thêm constructor mặc định
@AllArgsConstructor
public class EmailEvent {
    private String deduplicationId;
    private String to;
    private String subject;
    private String content;
    private String type;
    private Map<String, Object> templateData;
    private Map<String, Object> metadata;

    public Map<String, Object> getMetadata() {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        return metadata;
    }

    public static EmailEventBuilder passwordResetEvent(String email, String username, String token) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("username", username);
        templateData.put("token", token);

        return EmailEvent.builder()
                .to(email)
                .type("PASSWORD_RESET")
                .subject("Reset Your Password - Elevate Banking")
                .templateData(templateData);
        // return EmailEvent.builder()
        // .to(email)
        // .subject("Reset Your Password - Elevate Banking")
        // .templateData(Map.of(
        // "username", username,
        // "token", token
        // ));
    }

    public static EmailEventBuilder transactionEvent(String email, String subject, String content) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("subject", subject);
        templateData.put("message", content);
        templateData.put("bankName", "Elevate Banking");
        return EmailEvent.builder()
                .to(email)
                .type("TRANSACTION")
                .subject(subject)
                .content(content)
                .templateData(templateData);
    }
}