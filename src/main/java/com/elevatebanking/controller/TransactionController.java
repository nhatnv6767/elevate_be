package com.elevatebanking.controller;

import com.elevatebanking.dto.transaction.TransactionDTOs.*;
import com.elevatebanking.service.ITransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Operation(summary = "Process a new transfer between accounts")
    @PostMapping("/transfer")
    @PreAuthorize("hasRole('USER') or hasRole('TELLER')")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request) {
        log.info("Processing transfer request from account: {} to account: {}, amount: {}", request.getFromAccountId(), request.getToAccountId(), request.getAmount());
        return ResponseEntity.ok(transactionService.transfer(request));
    }

    @Operation(summary = "Process a new deposit to an account")
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('USER') or hasRole('TELLER')")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
        log.info("Processing deposit request to account: {}, amount: {}", request.getAccountId(), request.getAmount());
        return ResponseEntity.ok(transactionService.deposit(request));
    }

    @Operation(summary = "Process a new withdrawal from an account")
    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('USER') or hasRole('TELLER')")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        log.info("Processing withdrawal request from account: {}, amount: {}", request.getAccountId(), request.getAmount());
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
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("Fetching transaction history fro account: {}, from: {}, to: {}", accountId, startDate, endDate);
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountId, startDate, endDate));
    }

}
