package com.elevatebanking.service.transaction.config;

import com.elevatebanking.service.transaction.TransactionValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
public class TransactionLockManager implements AutoCloseable {
    private final String lockKey;
    private final String lockValue;
    private final TransactionValidationService validationService;
    private boolean lockAcquired = false;


    public TransactionLockManager(String lockKey, TransactionValidationService service) {
        this.lockKey = lockKey;
        this.lockValue = UUID.randomUUID().toString();
        this.validationService = service;
    }

    public boolean acquireLock() {
        try {
            lockAcquired = validationService.acquireLock(lockValue, lockKey);
            return lockAcquired;
        } catch (Exception e) {
            log.error("Failed to acquire lock: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        if (lockAcquired) {
            try {
                validationService.releaseLock(lockKey, lockValue);
                log.debug("Lock released successfully: {}", lockKey);
            } catch (Exception e) {
                log.error("Error releasing lock: {} - {}", lockKey, e.getMessage());
                // Force cleanup in case of errors
                validationService.clearStuckLocks(lockKey);
            }
        }
    }
}
