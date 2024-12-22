package com.elevatebanking.event;

import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.transaction.Transaction;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class TransactionEvent {
    private String eventId;
    private String eventType; // transaction.initiated, transaction.completed, ...
    private String transactionId;
    private TransactionType type; // DEPOSIT, WITHDRAWAL, TRANSFER
    private TransactionStatus status; // PENDING, COMPLETED, FAILED
    private BigDecimal amount;
    private AccountInfo fromAccount;
    private AccountInfo toAccount;
    private String description;
    private LocalDateTime timestamp;
    private Integer retryCount;


    @Data
    @NoArgsConstructor
    public static class AccountInfo {
        private String accountId;
        private String accountNumber;
        private String accountName;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
    }

    public TransactionEvent(Transaction transaction, String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.transactionId = transaction.getId();
        this.type = transaction.getType();
        this.status = transaction.getStatus();
        this.amount = transaction.getAmount();
        this.description = transaction.getDescription();
        this.timestamp = LocalDateTime.now();
        this.retryCount = 0;

        if (transaction.getFromAccount() != null) {
            this.fromAccount = new AccountInfo();
            this.fromAccount.setAccountId(transaction.getFromAccount().getId());
            this.fromAccount.setAccountNumber(transaction.getFromAccount().getAccountNumber());
            this.fromAccount.setAccountName(transaction.getFromAccount().getUser().getFullName());
            this.fromAccount.setBalanceBefore(transaction.getFromAccount().getBalance());
        }

        if (transaction.getToAccount() != null) {
            this.toAccount = new AccountInfo();
            this.toAccount.setAccountId(transaction.getToAccount().getId());
            this.toAccount.setAccountNumber(transaction.getToAccount().getAccountNumber());
            this.toAccount.setAccountName(transaction.getToAccount().getUser().getFullName());
            this.toAccount.setBalanceBefore(transaction.getToAccount().getBalance());
        }
    }

    public void updateBalances(BigDecimal fromBalance, BigDecimal toBalance) {
        if (this.fromAccount != null) {
            this.fromAccount.setBalanceAfter(fromBalance);
        }
        if (this.toAccount != null) {
            this.toAccount.setBalanceAfter(toBalance);
        }
    }

    public boolean canRetry(int maxRetries) {
        return this.retryCount < maxRetries;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean isCompleted() {
        return this.status == TransactionStatus.COMPLETED;
    }

    public boolean isFailed() {
        return this.status == TransactionStatus.FAILED;
    }
}
