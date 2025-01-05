package com.elevatebanking.controller;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.service.ITransactionService;
import com.elevatebanking.util.SecurityUtils;
import com.github.dockerjava.api.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transaction Management", description = "APIs for managing banking transactions")
@SecurityRequirement(name = "Bearer Authentication")
public class TransactionController {
    private final ITransactionService transactionService;
    private final IAccountService accountService;
    private final SecurityUtils securityUtils;


    @Operation(summary = "Process a new transfer between accounts")
    @PostMapping("/transfer")
    @PreAuthorize("hasRole('USER') or hasRole('TELLER')")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        String userId = securityUtils.getCurrentUserId();

        // get the account by number instead of id
        Account fromAccount = accountService.getAccountByNumber(request.getFromAccountNumber()).orElseThrow(() -> new RuntimeException("Source account not found"));


        if (!accountService.isAccountOwner(fromAccount.getId(), userId)) {
            throw new UnauthorizedException("User is not authorized to perform this operation");
        }

        log.info("Processing transfer request from account: {} to account: {}, amount: {}", request.getFromAccountNumber(),
                request.getToAccountNumber(), request.getAmount());
        return ResponseEntity.ok(transactionService.transfer(request));
    }

    @Operation(summary = "Process a new deposit to an account")
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('USER') or hasRole('TELLER')")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
        log.info("Processing deposit request to account: {}, amount: {}", request.getAccountNumber(), request.getAmount());
        return ResponseEntity.ok(transactionService.deposit(request));
    }

    @Operation(summary = "Process a new withdrawal from an account")
    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('USER') or hasRole('TELLER')")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        String userId = securityUtils.getCurrentUserId();

        // get the account by number instead of id
        Account account = accountService.getAccountByNumber(request.getAccountNumber()).orElseThrow(() -> new RuntimeException("Account not found"));

        if (!accountService.isAccountOwner(account.getId(), userId)) {
            throw new UnauthorizedException("User is not authorized to perform this operation");
        }
        log.info("Processing withdrawal request from account: {}, amount: {}", request.getAccountNumber(),
                request.getAmount());
        return ResponseEntity.ok(transactionService.withdraw(request));
    }

    @Operation(summary = "Get transaction details by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TELLER', 'ADMIN')")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String id) {
        log.info("Fetching transaction details for transaction ID: {}", id);
        return ResponseEntity.ok(transactionService.getTransaction(id));
    }

    @Operation(summary = "Get transaction history with optional date range")
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('USER', 'TELLER', 'ADMIN')")
    public ResponseEntity<List<TransactionHistoryResponse>> getTransactionHistory(
            @RequestParam String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        String userId = securityUtils.getCurrentUserId();
        if (!accountService.isAccountOwner(accountId, userId)) {
            throw new UnauthorizedException("User is not authorized to perform this operation");
        }

        log.info("Fetching transaction history fro account: {}, from: {}, to: {}", accountId, startDate, endDate);
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountId, startDate, endDate));
    }

    private boolean hasAdminOrTellerRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN") || r.getAuthority().equals("ROLE_TELLER"));
    }

}
