package com.elevatebanking.entity;

import jakarta.persistence.*;
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

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "term_months")
    private Integer termMonths;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "minimum_deposit", nullable = false, precision = 20, scale = 2)
    private BigDecimal minimumDeposit;

    @Column(name = "early_withdrawal_penalty", nullable = false, precision = 5, scale = 2)
    private BigDecimal earlyWithdrawalPenalty;

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