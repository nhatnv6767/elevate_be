package com.elevatebanking.entity.limit;

import com.elevatebanking.entity.base.BaseEntity;
import com.elevatebanking.entity.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "limit_exceptions")
@Getter
@Setter
@NoArgsConstructor
public class LimitException extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Exception start time is required")
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @NotNull(message = "Exception end time is required")
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @NotNull(message = "Exception limit is required")
    @DecimalMin(value = "0.0", message = "Exception limit must be greater than 0")
    @Column(name = "exception_limit", precision = 20, scale = 2)
    private BigDecimal exceptionLimit;

    @NotBlank(message = "Approval reason is required")
    @Column(name = "approval_reason")
    private String approvalReason;

    @NotBlank(message = "Approved by is required")
    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "is_active")
    private boolean active = true;
}
