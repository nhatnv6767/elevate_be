package com.elevatebanking.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill_payments")
@Getter
@Setter
@NoArgsConstructor
public class BillPayment {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "payment_id", columnDefinition = "VARCHAR(36)")
    private String id;

    @NotNull(message = "Account is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @NotNull(message = "Biller is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "biller_id", nullable = false)
    private Biller biller;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 18, fraction = 2, message = "Invalid amount format")
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Pattern(regexp = "^[A-Za-z0-9-]{3,100}$", message = "Invalid bill number format")
    @Column(name = "bill_number", length = 100)
    private String billNumber;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

enum PaymentStatus {
    PENDING, COMPLETED, FAILED
}
