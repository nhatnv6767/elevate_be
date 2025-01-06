package com.elevatebanking.service.transaction.config;

import com.elevatebanking.service.transaction.TransactionValidationService;

public class TransactionLockManager implements AutoCloseable {
    private final String lockKey;
    private final TransactionValidationService validationService;

    public TransactionLockManager(String lockKey, TransactionValidationService service) {
        this.lockKey = lockKey;
        this.validationService = service;
    }

    @Override
    public void close() {
        validationService.releaseLock(lockKey);
    }
}
