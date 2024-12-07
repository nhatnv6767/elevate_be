package com.elevatebanking.exception;

public class CustomDuplicateResourceException extends RuntimeException {
    public CustomDuplicateResourceException(String message) {
        super(message);
    }
}
