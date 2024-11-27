package com.elevatebanking.entity.account;

import com.elevatebanking.entity.base.BaseEntity;
import com.elevatebanking.entity.base.interfaces.Statusable;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.entity.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Account extends BaseEntity implements Statusable{


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


}


