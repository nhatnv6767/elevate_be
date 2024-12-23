package com.elevatebanking.exception;

public class TransactionProcessingException extends RuntimeException {
    private final boolean retryable;
    private final String transactionId;

    public TransactionProcessingException(String message, String transactionId, boolean retryable) {
        super(message);
        this.transactionId = transactionId;
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
