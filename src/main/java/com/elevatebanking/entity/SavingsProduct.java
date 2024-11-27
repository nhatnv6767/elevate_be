package com.elevatebanking.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "savings_products")
@Getter
@Setter
@NoArgsConstructor
public class SavingsProduct {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "product_id", columnDefinition = "VARCHAR(36)")
    private String id;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    @Column(name = "product_name", length = 100, nullable = false)
    private String productName;

    @Min(value = 1, message = "Term months must be at least 1")
    @Max(value = 120, message = "Term months cannot exceed 120")
    @Column(name = "term_months")
    private Integer termMonths;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.00", message = "Interest rate cannot be negative")
    @DecimalMax(value = "100.00", message = "Interest rate cannot exceed 100")
    @Digits(integer = 3, fraction = 2, message = "Invalid interest rate format")
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @NotNull(message = "Minimum deposit is required")
    @DecimalMin(value = "0.01", message = "Minimum deposit must be greater than 0")
    @Digits(integer = 18, fraction = 2, message = "Invalid minimum deposit format")
    @Column(name = "minimum_deposit", nullable = false, precision = 20, scale = 2)
    private BigDecimal minimumDeposit;

    @DecimalMin(value = "0.00", message = "Early withdrawal penalty cannot be negative")
    @DecimalMax(value = "100.00", message = "Early withdrawal penalty cannot exceed 100")
    @Digits(integer = 3, fraction = 2, message = "Invalid penalty format")
    @Column(name = "early_withdrawal_penalty", precision = 5, scale = 2)
    private BigDecimal earlyWithdrawalPenalty;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

enum ProductStatus {
    ACTIVE, INACTIVE
}