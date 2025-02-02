package com.elevatebanking.entity.account;

import com.elevatebanking.entity.base.BaseEntity;
import com.elevatebanking.entity.base.EntityConstants;
import com.elevatebanking.entity.base.interfaces.Status;
import com.elevatebanking.entity.base.interfaces.Statusable;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.entity.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Account extends BaseEntity implements Statusable {

    @NotNull(message = EntityConstants.REQUIRED_FIELD)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank(message = EntityConstants.REQUIRED_FIELD)
    @Pattern(regexp = EntityConstants.ACCOUNT_NUMBER_PATTERN, message = "Invalid account number format")
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

    @OneToMany(mappedBy = "fromAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Transaction> outgoingTransactions = new HashSet<>();

    @OneToMany(mappedBy = "toAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Transaction> incomingTransactions = new HashSet<>();


    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public void setStatus(Status status) {
        if (!(status instanceof AccountStatus)) {
            throw new IllegalArgumentException(EntityConstants.INVALID_ACCOUNT_STATUS);
        }
        this.status = (AccountStatus) status;
    }

    public void addOutgoingTransaction(Transaction transaction) {
        outgoingTransactions.add(transaction);
        transaction.setFromAccount(this);
    }
    
    public void removeOutgoingTransaction(Transaction transaction) {
        outgoingTransactions.remove(transaction);
        transaction.setFromAccount(null);
    }
    
    public void addIncomingTransaction(Transaction transaction) {
        incomingTransactions.add(transaction);
        transaction.setToAccount(this);
    }
    
    public void removeIncomingTransaction(Transaction transaction) {
        incomingTransactions.remove(transaction);
        transaction.setToAccount(null);
    }

    public Account(User user, String accountNumber) {
        this.user = user;
        this.accountNumber = accountNumber;
        this.balance = BigDecimal.ZERO;
        this.status = AccountStatus.ACTIVE;
    }

}


