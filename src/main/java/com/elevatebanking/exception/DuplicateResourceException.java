package com.elevatebanking.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ElevateBankingException {
    public DuplicateResourceException(String message) {
        super("DUPLICATE_RESOURCE", message, HttpStatus.BAD_REQUEST);
    }
}
