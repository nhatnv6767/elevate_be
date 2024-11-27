package com.elevatebanking.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "event_logs")
@NoArgsConstructor
public class EventLog {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(columnDefinition = "VARCHAR(36)", name = "event_id")
    private String id;

    @NotBlank(message = "Topic is required")
    @Size(min = 1, max = 100, message = "Topic must be between 1 and 100 characters")
    @Column(length = 100, nullable = false)
    private String topic;

    @NotBlank(message = "Event type is required")
    @Size(min = 1, max = 50, message = "Event type must be between 1 and 50 characters")
    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    @NotNull(message = "Payload is required")
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.PENDING;

    @PositiveOrZero(message = "Retry count cannot be negative")
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @AssertTrue(message = "Processed time must be after creation time")
    private boolean isValidProcessedTime() {
        if (processedAt != null && createdAt != null) {
            return processedAt.isAfter(createdAt);
        }
        return true;
    }

    @Max(value = 5, message = "Maximum retry attempts exceeded")
    public Integer getRetryCount() {
        return retryCount;
    }
}


enum EventStatus {
    PENDING, PROCESSED, FAILED, RETRY
}