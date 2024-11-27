package com.elevatebanking.exception;

import org.springframework.http.HttpStatus;

public class ElevateBankingException extends RuntimeException {
    private String errorCode;
    private HttpStatus status;

    public ElevateBankingException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
