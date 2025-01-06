package com.elevatebanking.entity.log;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import jakarta.persistence.Id;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedEvent {
    @Id
    private String id;
    private String topic;
    private String key;
    @Column(columnDefinition = "text")
    private String payload;
    private LocalDateTime failedAt;
    private String status;
    private String errorMessage;
}
