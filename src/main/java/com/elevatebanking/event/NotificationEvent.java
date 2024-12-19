package com.elevatebanking.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationEvent {
    private String eventId;
    private String userId; // user nao nhan thong bao
    private String title;
    private String message;
    private String transactionId;  // id cua giao dich lien quan
    private String type; // TRANSACTION, SECURITY, SYSTEM
    private String priority; // HIGH, MEDIUM, LOW
    private LocalDateTime timestamp;

    public enum NotificationType {
        TRANSACTION_INITIATED,
        TRANSACTION_COMPLETED,
        TRANSACTION_FAILED,
        SECURITY_ALERT,
        SYSTEM_NOTIFICATION
    }

    public enum Priority {
        HIGH,
        MEDIUM,
        LOW
    }

    public static NotificationEvent forTransaction(String userId, String transactionId, String type, String status) {
        String title = String.format("Transaction %s", status);
        String message = String.format("Your transaction (%s) has been %s. Transaction ID: %s", type.toLowerCase(), status.toLowerCase(), transactionId);

        return NotificationEvent.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .build();
    }
}
