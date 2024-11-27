package com.elevatebanking.service.imp;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.exception.InsufficientBalanceException;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.repository.AccountRepository;
import com.elevatebanking.repository.UserRepository;
import com.elevatebanking.service.IAccountService;
import com.elevatebanking.util.AccountNumberGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@Slf4j
public class AccountServiceImpl implements IAccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public AccountServiceImpl(AccountRepository accountRepository, UserRepository userRepository, AccountNumberGenerator accountNumberGenerator, RedisTemplate<String, String> redisTemplate) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Account createAccount(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Account account = new Account();
        account.setUser(user);
        account.setAccountNumber(accountNumberGenerator.generate());
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);

        return accountRepository.save(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> getAccountById(String id) {
        return accountRepository.findById(id);
    }

    @Override
    public Optional<Account> getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
    }

    @Override
    public List<Account> getAccountsByUserId(String userId) {
        return accountRepository.findByUserId(userId);
    }

    @Override
    public Account updateAccountStatus(String id, AccountStatus status) {
        Account account = getAccountById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        account.setStatus(status);
        return accountRepository.save(account);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String accountId) {
        String cachedBalance = redisTemplate.opsForValue().get("balance:" + accountId);
        if (cachedBalance != null) {
            return new BigDecimal(cachedBalance);
        }

        Account account = getAccountById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        // cache the balance for 5 minutes
        redisTemplate.opsForValue().set("balance:" + accountId, account.getBalance().toString(), 5, TimeUnit.MINUTES);
        return account.getBalance();
    }

    @Override
    public Account updateBalance(String accountId, BigDecimal newBalance) {
        Account account = getAccountById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOperationException("Balance cannot be negative");
        }

        account.setBalance(newBalance);
        Account updatedAccount = accountRepository.save(account);

        // update cache
        redisTemplate.opsForValue().set("balance:" + accountId, newBalance.toString(), 5, TimeUnit.MINUTES);
        return updatedAccount;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByAccountNumber(String accountNumber) {
        return accountRepository.existsByAccountNumber(accountNumber);
    }

    @Override
    public void validateAccount(String accountId, BigDecimal amount) {
        Account account = getAccountById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidOperationException("Account is not active");
        }

        if (amount != null && account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }
    }
}
