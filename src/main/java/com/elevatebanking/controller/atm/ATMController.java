package com.elevatebanking.controller.atm;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.atm.AtmMachine;
import com.elevatebanking.entity.log.AuditLog;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.exception.TooManyAttemptsException;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import com.elevatebanking.service.atm.AtmManagementService;
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
    private final AtmManagementService atmManagementService;

    private static final BigDecimal MAX_WITHDRAWAL_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal MIN_WITHDRAWAL_AMOUNT = new BigDecimal("1");
    private static final BigDecimal MIN_DEPOSIT_AMOUNT = new BigDecimal("5");

    // @Getter
    // @Setter
    // @AllArgsConstructor
    // private static class ErrorResponse {
    // private String message;
    // private String code;
    // }

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

            if (request.getAtmId() == null || request.getAtmId().trim().isEmpty()) {
                throw new InvalidOperationException("ATM ID cannot be null or empty");
            }

            // validate ATM exists and is active
            AtmMachine atm = atmManagementService.getAtmById(request.getAtmId());
            if (!"ACTIVE".equals(atm.getStatus())) {
                throw new InvalidOperationException("ATM is not active");
            }

            // validate withdrawal amount and denominations
            // BigDecimal totalAmount = calculateTotal(request.getRequestedDenominations());
            // if (!totalAmount.equals(request.getAmount())) {
            // throw new InvalidOperationException("Requested denominations don't match the
            // total amount");
            // }

            // validate withdrawal amount
            BigDecimal amount = request.getAmount();
            BigDecimal remainder = amount.remainder(MIN_WITHDRAWAL_AMOUNT);
            boolean isNotMultipleOfMinAmount = remainder.compareTo(BigDecimal.ZERO) != 0;

            if (amount.compareTo(MIN_WITHDRAWAL_AMOUNT) < 0 || isNotMultipleOfMinAmount) {
                throw new InvalidOperationException("Withdrawal amount must be at least $" + MIN_WITHDRAWAL_AMOUNT
                        + " and in multiples of $" + MIN_WITHDRAWAL_AMOUNT);
            }

            if (amount.compareTo(MAX_WITHDRAWAL_AMOUNT) > 0) {
                throw new InvalidOperationException("Withdrawal amount must be at most $" + MAX_WITHDRAWAL_AMOUNT);
            }

            // calculate amount money user wants to withdraw
            Map<Integer, Integer> requestedDenominations = atmManagementService
                    .calculateOptimalDenominations(request.getAtmId(), request.getAmount());

            // check if atm has enough bills of requested denominations
            if (!atmManagementService.canProcessWithdrawal(request.getAtmId(), request.getAmount(),
                    requestedDenominations)) {
                throw new InvalidOperationException("ATM doesn't have enough bills to process this withdrawal");
            }

            request.setRequestedDenominations(requestedDenominations);

            // process the withdrawal
            WithdrawalResponse response = transactionService.withdraw(request);

            // update ATM denominations
            Map<Integer, Integer> updatedDenominations = atmManagementService
                    .subtractDenominations(atm.getDenominations(), requestedDenominations);
            atmManagementService.updateAtmDenominations(response.getAtmId(), updatedDenominations);

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
                            "atmId", request.getAtmId(),
                            "dispensedDenominations", requestedDenominations),
                    AuditLog.AuditStatus.SUCCESS);
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
                            "location", "ATM_001"),
                    AuditLog.AuditStatus.FAILED);
            throw e;
        }
    }

    // private BigDecimal calculateTotal(Map<Integer, Integer> denominations) {
    // return denominations.entrySet().stream()
    // .map(entry -> new BigDecimal(entry.getKey() * entry.getValue()))
    // .reduce(BigDecimal.ZERO, BigDecimal::add);
    // }

}
