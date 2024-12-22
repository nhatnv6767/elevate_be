package com.elevatebanking.exception;

import org.springframework.http.HttpStatus;

public class TransactionLimitExceededException extends ElevateBankingException {
    public TransactionLimitExceededException(String message) {
        super("TRANSACTION_LIMIT_EXCEEDED", message, HttpStatus.BAD_REQUEST);
    }
}
