package com.elevatebanking.service;

import com.elevatebanking.dto.accounts.AccountDTOs;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface IAccountService {
    Account createAccount(String userId);

    Optional<Account> getAccountById(String id);

    // accountNumber
    Optional<Account> getAccountByNumber(String accountNumber);

    List<Account> getAccountsByUserId(String userId);

    Account updateAccountStatus(String id, AccountStatus status);

    BigDecimal getBalance(String accountId);

    Account updateBalance(String accountId, BigDecimal newBalance);

    boolean existsByAccountNumber(String accountNumber);

    void validateAccount(String accountId, BigDecimal amount);

    AccountDTOs.AccountBalanceResponse getBalanceInfo(String id);

    boolean isAccountOwner(String accountId, String userId);

    void validateAccountOwnership(String accountId, String userId);
}
