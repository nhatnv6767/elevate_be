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
import java.util.*;

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
    private String userId;

    private Map<String, Object> metadata; // additional data
    private String errorMessage; // error message
    private List<String> processedSteps; // list of processed steps

    public enum EventType {
        TRANSACTION_INITIATED("transaction.initiated"),
        TRANSACTION_VALIDATED("transaction.validated"),
        TRANSACTION_COMPLETED("transaction.completed"),
        TRANSACTION_FAILED("transaction.failed"),
        TRANSACTION_ROLLBACK("transaction.rollback");

        private final String value;

        EventType(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }

        public static EventType fromValue(String value) {
            for (EventType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid event type: " + value);
        }
    }


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

        // new fileds
        this.metadata = new HashMap<>();
        this.processedSteps = new ArrayList<>();
        this.addProcessStep("EVENT_CREATED");

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

    public boolean canRetry() {
        return this.retryCount < 3 &&
                this.status != TransactionStatus.COMPLETED &&
                this.status != TransactionStatus.ROLLED_BACK;
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

    // new methods

    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    public void addProcessStep(String step) {
        if (this.processedSteps == null) {
            this.processedSteps = new ArrayList<>();
        }
        this.processedSteps.add(step + " at " + LocalDateTime.now());
    }

    public void setError(String message) {
        this.errorMessage = message;
        this.addProcessStep("ERROR: " + message);

    }

    public boolean isExpired() {
        return LocalDateTime.now().minusMinutes(15).isAfter(this.timestamp);
    }

    public boolean isRetryable() {
        // Một giao dịch được coi là có thể retry nếu:
        // 1. Chưa vượt quá số lần retry tối đa
        // 2. Chưa hết hạn
        // 3. Không ở trạng thái cuối cùng (COMPLETED hoặc ROLLED_BACK)
        // 4. Không phải lỗi nghiêm trọng
        return canRetry() &&
                !isExpired() &&
                status != TransactionStatus.COMPLETED &&
                status != TransactionStatus.ROLLED_BACK &&
                (errorMessage == null || !errorMessage.contains("CRITICAL"));
    }
}
