package com.elevatebanking.controller;

import com.elevatebanking.dto.accounts.AccountDTOs.*;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;
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

    // TODO: 1. Cải tiến là lúc tạo tài khoản phương thức Post (/api/v1/accounts) thì thêm được cả balance cho tài khoản luôn, và nếu có lỗi trả về thì phải hiển thị rõ
    // TODO: ví dụ như lỗi khi user đó có max tài khoản rồi thì trả về nội dung tương ứng
    // TODO: 2. Vấn đề nữa là khi gửi yêu cầu reset mật khẩu bằng api  api/v1/auth/forgot-password thì dữ liệu lưu vào redis, vậy người dùng cứ spam api này thì có phải dữ liệu bị lưu liên tục vào redis
    // TODO: cho đến 1 lúc nào đó die server không, có cơ chế để xoá dữ liệu cũ rồi mới lưu dữ liệu cũ không nhỉ, hay có cách gì khác để tối ưu
    @Operation(summary = "Create new account for user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    @PostMapping
    public ResponseEntity<?> createAccount(@RequestParam String userId) {
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "User ID is required"));
        }

        try {
            Account newAccount = accountService.createAccount(userId);

            AccountResponse response = accountMapper.accountToAccountResponse(newAccount);

            return ResponseEntity.ok(Map.of("message", "Account created successfully", "account", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            "message", "Error creating account"));
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
