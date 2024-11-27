package com.elevatebanking.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "savings_accounts")
@Getter
@Setter
@NoArgsConstructor
public class SavingsAccount {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "savings_id", columnDefinition = "VARCHAR(36)")
    private String id;

    @NotNull(message = "Account is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @NotNull(message = "Savings product is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private SavingsProduct product;

    @NotNull(message = "Principal amount is required")
    @DecimalMin(value = "0.01", message = "Principal amount must be greater than 0")
    @Digits(integer = 18, fraction = 2, message = "Invalid principal amount format")
    @Column(name = "principal_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal principalAmount;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.00", message = "Interest rate cannot be negative")
    @DecimalMax(value = "100.00", message = "Interest rate cannot exceed 100")
    @Digits(integer = 3, fraction = 2, message = "Invalid interest rate format")
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @NotNull(message = "Start date is required")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Future(message = "Maturity date must be in the future")
    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SavingsStatus status = SavingsStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @AssertTrue(message = "Maturity date must be after start date")
    private boolean isValidDateRange() {
        if (startDate != null && maturityDate != null) {
            return maturityDate.isAfter(startDate);
        }
        return true;
    }

    @AssertTrue(message = "Principal amount must be at least the minimum deposit")
    private boolean isValidPrincipalAmount() {
        if (principalAmount != null && product != null && product.getMinimumDeposit() != null) {
            return principalAmount.compareTo(product.getMinimumDeposit()) >= 0;
        }
        return true;
    }
}


