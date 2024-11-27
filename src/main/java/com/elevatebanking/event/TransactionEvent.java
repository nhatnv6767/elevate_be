package com.elevatebanking.event;

import com.elevatebanking.entity.transaction.Transaction;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class TransactionEvent {
    private String transactionId;
    private String fromAccount;
    private String toAccount;
    private String amount;
    private String type;
    private String status;
    private LocalDateTime timestamp;

    public TransactionEvent(Transaction transaction) {
        this.transactionId = transaction.getId();
        this.fromAccount = transaction.getFromAccount() != null ? transaction.getFromAccount().getAccountNumber() : null;
        this.toAccount = transaction.getToAccount() != null ? transaction.getToAccount().getAccountNumber() : null;
        this.amount = transaction.getAmount().toString();
        this.type = transaction.getType().name();
        this.status = transaction.getStatus().name();
        this.timestamp = LocalDateTime.now();
    }
}
