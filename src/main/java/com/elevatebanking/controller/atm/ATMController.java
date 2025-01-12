package com.elevatebanking.controller.atm;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.log.AuditLog;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.exception.TooManyAttemptsException;
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/atm")
@Tag(name = "ATM Operations", description = "APIs for ATM operations like withdraw and deposit")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
@Slf4j
public class ATMController {
    private final ITransactionService transactionService;
    private final SecurityUtils securityUtils;
    private final IAccountService accountService;
    private final TransactionValidationService validationService;
    private final AuditLogService auditLogService;

    private static final BigDecimal MAX_WITHDRAWAL_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal MIN_WITHDRAWAL_AMOUNT = new BigDecimal("5");
    private static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("5");

//    @Getter
//    @Setter
//    @AllArgsConstructor
//    private static class ErrorResponse {
//        private String message;
//        private String code;
//    }

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Withdraw money from ATM")
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@Valid @RequestBody WithdrawRequest request) throws InterruptedException {
        String userId = securityUtils.getCurrentUserId();
        String lockKey = "atm_operation:" + userId;

        try (TransactionLockManager lockManager = new TransactionLockManager(lockKey, validationService)) {
            if (!lockManager.acquireLock()) {
                throw new TooManyAttemptsException("ATM is busy, please try again in a few seconds");
            }

            // validate account ownership
            Account account = accountService.getAccountByNumber(request.getAccountNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

            if (!accountService.isAccountOwner(account.getId(), userId)) {
                throw new UnauthorizedException("Not authorized to access this account");
            }

            // validate withdrawal amount
            if (request.getAmount().compareTo(MIN_WITHDRAWAL_AMOUNT) < 0 ||
                    request.getAmount().remainder(MIN_WITHDRAWAL_AMOUNT).compareTo(BigDecimal.ZERO) != 0) {
                throw new InvalidOperationException("Withdrawal amount must be at least $" + MAX_WITHDRAWAL_AMOUNT + " and in multiples of $" + MIN_WITHDRAWAL_AMOUNT);
            }

            WithdrawalResponse response = transactionService.withdraw(request);
            // log successful withdrawal
            auditLogService.logEvent(
                    userId,
                    "ATM_WITHDRAWAL",
                    "TRANSACTION",
                    response.getTransactionId(),
                    request,
                    Map.of(
                            "status", "SUCCESS",
                            "amount", request.getAmount(),
                            "location", "ATM_001"
                    ),
                    AuditLog.AuditStatus.SUCCESS
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing withdrawal for user: {}", userId, e);
            auditLogService.logEvent(
                    userId,
                    "ATM_WITHDRAWAL_FAILED",
                    "TRANSACTION",
                    null,
                    request,
                    Map.of(
                            "error", e.getMessage(),
                            "location", "ATM_001"
                    ),
                    AuditLog.AuditStatus.FAILED
            );
            throw e;
        }
    }

}


