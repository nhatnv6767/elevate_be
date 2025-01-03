package com.elevatebanking.entity.limit;

import com.elevatebanking.entity.base.BaseEntity;
import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.enums.UserStatus;
import com.elevatebanking.entity.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.Set;

@Entity
@Table(name = "transaction_limits")
@Getter
@Setter
@NoArgsConstructor
public class TransactionLimit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Single transaction limit is required")
    @DecimalMin(value = "0.0", message = "Single transaction limit must be greater than 0")
    @Column(name = "single_transaction_limit", nullable = false, precision = 20, scale = 2)
    private BigDecimal singleTransactionLimit;

    @NotNull(message = "Daily limit is required")
    @DecimalMin(value = "0.0", message = "Daily limit must be greater than 0")
    @Column(name = "daily_limit", nullable = false, precision = 20, scale = 2)
    private BigDecimal dailyLimit;

    @NotNull(message = "Weekly limit is required")
    @DecimalMin(value = "0.0", message = "Monthly limit must be greater than 0")
    @Column(name = "monthly_limit", nullable = false, precision = 20, scale = 2)
    private BigDecimal monthlyLimit;

    @Min(value = 1, message = "Max transactions per minute must be greater than 1")
    @Column(name = "max_transactions_per_minute")
    private int maxTransactionsPerMinute = 3;

    @Min(value = 1, message = "Max transactions per day must be greater than 1")
    @Column(name = "max_transactions_per_day")
    private int maxTransactionsPerDay = 100;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "allowed_transaction_types", joinColumns = @JoinColumn(name = "limit_id"))
    @Column(name = "transaction_type")
    private Set<TransactionType> allowedTransactionTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
}
