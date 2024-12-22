package com.elevatebanking.exception;

import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends ElevateBankingException {
    public TransactionNotFoundException(String message) {
        super("TRANSACTION_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
