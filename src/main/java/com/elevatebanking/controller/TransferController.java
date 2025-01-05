package com.elevatebanking.controller;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import com.elevatebanking.service.nonImp.AuditLogService;
import com.elevatebanking.util.SecurityUtils;
import com.github.dockerjava.api.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.proto.ErrorResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Initiate a new transfer")
    @PostMapping
    public ResponseEntity<TransactionResponse> initiateTransfer(
            @Valid @RequestBody TransferRequest request
    ) {
        log.debug("Received transfer request: {} -> {}, amount: {}", request.getFromAccountNumber(), request.getToAccountNumber(), request.getAmount());

        // verify the user owns the source account
        String userId = securityUtils.getCurrentUserId();
        try {
            Account fromAccount = accountService.getAccountByNumber(request.getFromAccountNumber()).orElseThrow(() -> new RuntimeException("Source account not found"));
            Account toAccount = accountService.getAccountByNumber(request.getToAccountNumber()).orElseThrow(() -> new RuntimeException("Destination account not found"));
            // check if fromAccount or toAccount is null
            if (fromAccount == null || toAccount == null) {
                throw new RuntimeException("Account not found");
            }
            if (!accountService.isAccountOwner(fromAccount.getId(), userId)) {
                auditLogService.logEvent(
                        userId,
                        "UNAUTHORIZED_TRANSFER_ATTEMPT",
                        "ACCOUNT",
                        fromAccount.getId(),
                        null,
                        Map.of("requestedAmount", request.getAmount())
                );
                throw new UnauthorizedException("User is not authorized to perform this operation");
            }

            // process the transfer
            TransactionResponse response = transactionService.transfer(request);

            auditLogService.logEvent(
                    userId,
                    "TRANSFER_COMPLETED",
                    "TRANSACTION",
                    response.getTransactionId(),
                    Map.of(
                            "fromAccount", fromAccount,
                            "toAccount", toAccount,
                            "initialBalance", fromAccount.getBalance()
                    ),
                    Map.of(
                            "amount", request.getAmount(),
                            "finalBalance", fromAccount.getBalance().subtract(request.getAmount()),
                            "status", "COMPLETED"
                    )
            );

            log.info("Transfer processed successfully: {}", response.getTransactionId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create transfer", e);
            auditLogService.logEvent(
                    userId,
                    "TRANSFER_FAILED",
                    "TRANSACTION",
                    null,
                    request,
                    Map.of("error", e.getMessage())
            );
            throw new RuntimeException("Failed to create transfer", e);
        }
    }

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get transfer status")
    @GetMapping("/{transferId}")
    public ResponseEntity<TransactionResponse> getTransferStatus(@PathVariable String transferId) {
        log.debug("Fetching transfer status for ID: {}", transferId);
        TransactionResponse response = transactionService.getTransaction(transferId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get user's recent transfers")
    @GetMapping("/history")
    public ResponseEntity<List<TransactionHistoryResponse>> getTransferHistory(
            @RequestParam String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        // verify account ownership
        String userId = securityUtils.getCurrentUserId();
        auditLogService.logEvent(
                userId,
                "VIEW_TRANSFER_HISTORY",
                "ACCOUNT",
                accountId,
                null,
                Map.of("startDate", startDate, "endDate", endDate)
        );
        if (!accountService.isAccountOwner(accountId, userId)) {
            auditLogService.logEvent(
                    userId,
                    "UNAUTHORIZED_HISTORY_ACCESS",
                    "ACCOUNT",
                    accountId,
                    null,
                    Map.of("startDate", startDate, "endDate", endDate)
            );
            throw new UnauthorizedException("Not authorized to view this account's transfers");
        }
        List<TransactionHistoryResponse> history = transactionService.getTransactionHistory(accountId, startDate, endDate);
        return ResponseEntity.ok(history);
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
