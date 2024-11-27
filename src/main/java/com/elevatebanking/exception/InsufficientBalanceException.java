package com.elevatebanking.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends ElevateBankingException {
    public InsufficientBalanceException(String message) {
        super("INSUFFICIENT_BALANCE", message, HttpStatus.BAD_REQUEST);
    }
}
