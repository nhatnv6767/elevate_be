package com.elevatebanking.controller;

import com.elevatebanking.dto.accounts.AccountDTOs.*;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.mapper.AccountMapper;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.util.SecurityUtils;
import com.github.dockerjava.api.exception.UnauthorizedException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
// TODO: add DTO for request and response

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Account Management", description = "APIs for managing bank accounts")
@SecurityRequirement(name = "Bearer Authentication")
public class AccountController {

    private final IAccountService accountService;
    private final AccountMapper accountMapper;

    @GetMapping("/my-accounts")
    public ResponseEntity<List<AccountSummaryResponse>> getMyAccounts() {
        String userId = SecurityUtils.getCurrentUserId();
        List<Account> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accountMapper.accountsToSummaryResponses(accounts));
    }

    @Operation(summary = "Create new account for user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    @PostMapping
    public ResponseEntity<?> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        if (request == null || request.getUserId() == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Missing required fields body"));
        }

        try {
            Account newAccount = accountService.createAccount(request.getUserId(), request.getInitialBalance());
            AccountResponse response = accountMapper.accountToAccountResponse(newAccount);

            return ResponseEntity.ok(Map.of("message", "Account created successfully", "account", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("message", "Error creating account", "error", e.getMessage()));
        }
    }

    @Operation(summary = "Get account details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String id) {

        String userId = SecurityUtils.getCurrentUserId();

        return accountService.getAccountById(id)
                .filter(account -> accountService.isAccountOwner(id, userId))
                .map(
                        account -> ResponseEntity.ok(accountMapper.accountToAccountResponse(account)))
                .orElseThrow(() -> new UnauthorizedException("Not authorized to view account"));
    }

    @Operation(summary = "Get account details by account number")
    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccountByNumber(@PathVariable String accountNumber) {
        //
        return accountService.getAccountByNumber(accountNumber).map(
                        account -> ResponseEntity.ok(accountMapper.accountToAccountResponse(account)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get all accounts for user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountSummaryResponse>> getUserAccounts(@PathVariable String userId) {
        List<Account> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accountMapper.accountsToSummaryResponses(accounts));
    }

    @Operation(summary = "Get account balance")
    @GetMapping("/{id}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        if (!accountService.isAccountOwner(id, userId)) {
            throw new UnauthorizedException("Not authorized to view account balance");
        }
        return ResponseEntity.ok(accountService.getBalanceInfo(id));
    }

    // 3d60576c-dc75-458f-8d21-1de38a5600cd

    @Operation(summary = "Update account status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    @PutMapping("/{id}/status")
    public ResponseEntity<AccountResponse> updateStatus(
            @PathVariable String id,
            @RequestParam AccountStatus status) {
        Account updatedAccount = accountService.updateAccountStatus(id, status);
        return ResponseEntity.ok(accountMapper.accountToAccountResponse(updatedAccount));
    }

}
