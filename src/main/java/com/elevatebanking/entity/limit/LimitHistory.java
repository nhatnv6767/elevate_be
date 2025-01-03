package com.elevatebanking.entity.limit;

import com.elevatebanking.entity.base.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "limit_histories")
@Getter
@Setter
@NoArgsConstructor
public class LimitHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_limit_id", nullable = false)
    private TransactionLimit transactionLimit;

    @NotBlank(message = "Changed field is required")
    @Column(name = "changed_field", nullable = false)
    private String changedField;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @NotBlank(message = "Changed reason is required")
    @Column(name = "change_reason")
    private String changeReason;

    @NotNull(message = "Changed by is required")
    @Column(name = "changed_by")
    private String changedBy;
}
