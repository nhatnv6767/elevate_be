package com.elevatebanking.exception;

public class TransactionRollbackException extends RuntimeException {
    private final String transactionId;

    public TransactionRollbackException(String message, String transactionId) {
        super(message);
        this.transactionId = transactionId;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
