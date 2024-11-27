package com.elevatebanking.entity.account;

import com.elevatebanking.entity.enums.AccountStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "account_id", columnDefinition = "VARCHAR(36)")
    private String id;

    @NotNull(message = "User is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{10,20}$", message = "Invalid account number format")
    @Column(name = "account_number", length = 20, unique = true, nullable = false)
    private String accountNumber;

    @NotNull(message = "Balance is required")
    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    @Digits(integer = 18, fraction = 2, message = "Invalid balance format")
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


}


