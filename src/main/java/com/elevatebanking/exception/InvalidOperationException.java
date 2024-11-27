package com.elevatebanking.exception;

import org.springframework.http.HttpStatus;

public class InvalidOperationException extends ElevateBankingException {
    public InvalidOperationException(String message) {
        super("INVALID_OPERATION", message, HttpStatus.BAD_REQUEST);
    }
}
