package com.elevatebanking.controller;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.service.IAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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

    @Operation(summary = "Create new account for user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    @PostMapping
    public ResponseEntity<?> createAccount(@RequestParam String userId) {
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "User ID is required")
            );
        }

        try {
            Account newAccount = accountService.createAccount(userId);
            return ResponseEntity.ok(Map.of("message", "Account created successfully", "account", newAccount));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "message", "Error creating account"
                    )
            );
        }
    }

    @Operation(summary = "Get account details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable String id) {
        return accountService.getAccountById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get account details by account number")
    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<Account> getAccountByNumber(@PathVariable String accountNumber) {
        //
        return accountService.getAccountByNumber(accountNumber).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get all accounts for user")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Account>> getUserAccounts(@PathVariable String userId) {
        List<Account> accounts = accountService.getAccountsByUserId(userId);
        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "Get account balance")
    @GetMapping("/{id}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String id) {
        BigDecimal balance = accountService.getBalance(id);
        return ResponseEntity.ok(balance);
    }

    // 3d60576c-dc75-458f-8d21-1de38a5600cd

    @Operation(summary = "Update account status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    @PutMapping("/{id}/status")
    public ResponseEntity<Account> updateStatus(
            @PathVariable String id,
            @RequestParam AccountStatus status
    ) {
        Account updatedAccount = accountService.updateAccountStatus(id, status);
        return ResponseEntity.ok(updatedAccount);
    }


}
