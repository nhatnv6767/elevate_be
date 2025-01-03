package com.elevatebanking.entity.log;

import com.elevatebanking.entity.base.AuditableEntity;
import com.elevatebanking.entity.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AuditLog extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @NotBlank(message = "Action is required")
    @Size(min = 1, max = 50, message = "Action must be between 1 and 50 characters")
    @Column(length = 50, nullable = false)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(columnDefinition = "jsonb")
    private String details;


    @Column(name = "previous_state", columnDefinition = "jsonb")
    private String previousState;

    @Column(name = "current_state", columnDefinition = "jsonb")
    private String currentState;

    @Pattern(regexp = "^([0-9]{1,3}\\.){3}[0-9]{1,3}$", message = "Invalid IP address format")
    @Column(name = "ip_address")
    private String ipAddress;

    @Size(max = 255)
    @Column(name = "user_agent")
    private String userAgent;

   
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private AuditStatus status = AuditStatus.SUCCESS;

    public enum AuditStatus {
        SUCCESS,
        FAILED,
        PENDING
    }


    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;


}
