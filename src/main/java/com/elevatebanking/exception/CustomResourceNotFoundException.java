package com.elevatebanking.exception;

public class CustomResourceNotFoundException extends RuntimeException {
    public CustomResourceNotFoundException(String message) {
        super(message);
    }
}
