package com.elevatebanking.controller;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.log.AuditLog;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.exception.TransactionLimitExceededException;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import com.elevatebanking.service.nonImp.AuditLogService;
import com.elevatebanking.service.transaction.TransactionValidationService;
import com.elevatebanking.service.transaction.config.TransactionLockManager;
import com.elevatebanking.util.SecurityUtils;
import com.github.dockerjava.api.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Money Transfer", description = "APIs for handling money transfers between accounts")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
@Slf4j
public class TransferController {
    private final ITransactionService transactionService;
    private final SecurityUtils securityUtils;
    private final IAccountService accountService;
    private final AuditLogService auditLogService;
    private final TransactionValidationService validationService;
//    private final TransactionLockManager lockManager;

    @Data
    @AllArgsConstructor
    private static class ErrorResponse {
        private String message;
        private String code;
    }

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Initiate a new transfer")
    @PostMapping
    public ResponseEntity<?> transfer(@Valid @RequestBody TransferRequest request) {
        String userId = securityUtils.getCurrentUserId();
        String lockKey = "transaction_frequency:" + userId;

        try (TransactionLockManager lockManager = new TransactionLockManager(lockKey, validationService)) {
            // First, try to clear any potential stuck locks
//            validationService.clearStuckLocks(userId);

            // Try to acquire lock with retry mechanism
            if (!lockManager.acquireLock()) {
                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ErrorResponse(
                                "System is busy, please try again in a few moments",
                                "LOCK_ACQUISITION_FAILED"
                        ));
            }
            try {
                TransactionResponse response = transactionService.transfer(request);
                response.setTimestamp(LocalDateTime.now());
                return ResponseEntity.ok(response);
            } catch (TransactionLimitExceededException e) {
                log.warn("Transaction limit exceeded for user: {}", userId, e);
                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(new ErrorResponse(e.getMessage(), "LIMIT_EXCEEDED"));
            }

        } catch (Exception e) {
            log.error("Error processing transfer for user: {}", userId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(
                            "An error occurred while processing your transfer",
                            "INTERNAL_ERROR"
                    ));
        }
    }
//    public ResponseEntity<?> initiateTransfer(
//            @Valid @RequestBody TransferRequest request) {
//        log.debug("Received transfer request: {} -> {}, amount: {}", request.getFromAccountNumber(),
//                request.getToAccountNumber(), request.getAmount());
//        String userId = securityUtils.getCurrentUserId();
//        String lockKey = "transaction_frequency:" + userId;
//        try {
//            TransactionResponse response = transactionService.transfer(request);
//            if (!transactionValidationService.acquireLock(userId, lockKey)) {
//                String errorMessage = "Hệ thống đang bận, vui lòng thử lại sau";
//                auditLogService.logEvent(
//                        userId,
//                        "TRANSFER_FAILED",
//                        "TRANSACTION",
//                        response.getTransactionId(),
//                        request,
//                        Map.of(
//                                "error", errorMessage,
//                                "lockKey", lockKey,
//                                "timestamp", LocalDateTime.now()
//                        ),
//                        AuditLog.AuditStatus.FAILED
//                );
//                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
//                        .body(new ErrorResponse());
//            }
//
//
//            auditLogService.logEvent(
//                    userId,
//                    "TRANSFER_COMPLETED",
//                    "TRANSACTION",
//                    response.getTransactionId(),
//                    request,
//                    Map.of("status", "SUCCESS"),
//                    AuditLog.AuditStatus.SUCCESS);
//            log.info("Transfer successfully completed with transaction ID: {}", response.getTransactionId());
//            return ResponseEntity.ok(response);
//
//        } catch (InvalidOperationException e) {
//            log.error("Lỗi xác thực giao dịch", e);
//            auditLogService.logEvent(
//                    userId,
//                    "TRANSFER_FAILED",
//                    "TRANSACTION",
//                    null,
//                    request,
//                    Map.of("error", e.getMessage()),
//                    AuditLog.AuditStatus.FAILED);
//            return ResponseEntity.badRequest().body("Error when processing transaction: " + e.getMessage());
//
//        } catch (Exception e) {
//            log.error("Lỗi hệ thống khi thực hiện giao dịch", e);
//            auditLogService.logEvent(
//                    userId,
//                    "TRANSFER_FAILED",
//                    "TRANSACTION",
//                    null,
//                    request,
//                    Map.of("error", e.getMessage()),
//                    AuditLog.AuditStatus.FAILED);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body("Error when processing transaction: " + e.getMessage());
//        } finally {
//            transactionValidationService.releaseLock(lockKey);
//        }
//
//    }

    private void handleTransactionError(String userId, TransferRequest request,
                                        Exception e, String logMessage) {
        log.error(logMessage, e);
        auditLogService.logEvent(
                userId,
                "TRANSFER_FAILED",
                "TRANSACTION",
                null,
                request,
                Map.of(
                        "error", e.getMessage(),
                        "errorType", e.getClass().getSimpleName(),
                        "timestamp", LocalDateTime.now()
                ),
                AuditLog.AuditStatus.FAILED
        );
    }

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get transfer status")
    @GetMapping("/{transferId}")
    public ResponseEntity<TransactionResponse> getTransferStatus(@PathVariable String transferId) {
        log.debug("Fetching transfer status for ID: {}", transferId);

        // get current user id
        String userId = securityUtils.getCurrentUserId();
        // get the transaction

        Transaction transaction = transactionService.getTransactionById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found " + transferId));

        // check if user is involved in the transaction
        boolean isInvolved = false;
        if (transaction.getFromAccount() != null) {
            isInvolved = accountService.isAccountOwner(transaction.getFromAccount().getId(), userId);
        }
        if (!isInvolved && transaction.getToAccount() != null) {
            isInvolved = accountService.isAccountOwner(transaction.getToAccount().getId(), userId);
        }

        if (!isInvolved) {
            log.warn("Unauthorized access attempt - userId: {}, transferId: {}", userId, transferId);
            throw new UnauthorizedException("Not authorized to view this transfer");
        }

        TransactionResponse response = transactionService.getTransaction(transferId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get user's recent transfers")
    @GetMapping("/history")
    public ResponseEntity<Page<TransactionHistoryResponse>> getTransferHistory(
            @RequestParam String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20) Pageable page) {

        try {
            if (startDate != null && endDate != null) {
                if (startDate.isAfter(endDate)) {
                    throw new IllegalArgumentException("startDate must be before endDate");
                }
                if (endDate.isAfter(LocalDateTime.now())) {
                    throw new IllegalArgumentException("endDate must be before current time");
                }
                if (ChronoUnit.DAYS.between(startDate, endDate) > 365) {
                    throw new IllegalArgumentException("Date range must be within 365 days");
                }
            }
            log.debug("Fetching transfer history for account: {}", accountId);
            // verify account ownership
            String userId = securityUtils.getCurrentUserId();
            log.debug("Current userId: {}", userId);

            // log account ownership check
            boolean isOwner = accountService.isAccountOwner(accountId, userId);
            log.debug("Is account owner: {}", isOwner);

            if (!isOwner) {
                log.warn("Unauthorized access attempt - userId: {}, accountId: {}", userId, accountId);
                auditLogService.logEvent(
                        userId,
                        "UNAUTHORIZED_HISTORY_ACCESS",
                        "ACCOUNT",
                        accountId,
                        null,
                        Map.of("startDate", startDate, "endDate", endDate),
                        AuditLog.AuditStatus.FAILED);
                throw new UnauthorizedException("Not authorized to view this account's transfers");
            }

//            auditLogService.logEvent(
//                    userId,
//                    "VIEW_TRANSFER_HISTORY",
//                    "ACCOUNT",
//                    accountId,
//                    null,
//                    Map.of("startDate", startDate, "endDate", endDate),
//                    AuditLog.AuditStatus.SUCCESS);
            log.debug("Fetching transfer history for account: {}", accountId);
            Page<TransactionHistoryResponse> history = transactionService.getTransactionHistory(accountId, startDate,
                    endDate, page);
            log.debug("Found {} transactions for account: {}", history.getTotalElements(), accountId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            auditLogService.logEvent(
                    securityUtils.getCurrentUserId(),
                    "VIEW_TRANSFER_HISTORY_FAILED",
                    "ACCOUNT",
                    accountId,
                    null,
                    Map.of("error", e.getMessage()),
                    AuditLog.AuditStatus.FAILED);
            throw e;
        }
    }

    @Operation(summary = "Cancel a pending transfer")
    @DeleteMapping("/{transferId}")
    public ResponseEntity<Void> cancelTransfer(@PathVariable String transferId) {
        String userId = securityUtils.getCurrentUserId();

        // verify ownership and cancellation eligibility
        TransactionResponse transfer = transactionService.getTransaction(transferId);
        if (!accountService.isAccountOwner(transfer.getFromAccount(), userId)) {
            throw new UnauthorizedException("Not authorized to cancel this transfer");
        }
        transactionService.cancelTransaction(transferId);
        return ResponseEntity.noContent().build();
    }
}
