package com.elevatebanking.service.imp;

import com.elevatebanking.dto.accounts.AccountDTOs;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.entity.enums.UserStatus;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    @Transactional
    public Account createAccount(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidOperationException("Cannot create account for inactive user");
        }

        Account account = new Account();
        account.setUser(user);
        account.setAccountNumber(accountNumberGenerator.generate());
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);

        Account savedAccount = accountRepository.save(account);
        log.info("Created new account {} for user {}", account.getAccountNumber(), user.getUsername());
        return savedAccount;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> getAccountById(String id) {
        return accountRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> getAccountsByUserId(String userId) {
        return accountRepository.findByUserId(userId);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    @Transactional(readOnly = true)
    public Account updateAccountStatus(String id, AccountStatus status) {
        Account account = getAccountById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // Froze account that means when have trouble with transaction, and wanna refund the money
        if (account.getStatus() == AccountStatus.FROZEN && status != AccountStatus.ACTIVE) {
            throw new InvalidOperationException("Frozen account can only be activated");
        }

        account.setStatus(status);
        Account updatedAccount = accountRepository.save(account);
        log.info("Updated account {} status to {}", account.getAccountNumber(), status);

        return updatedAccount;
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
        redisTemplate.opsForValue().set(
                "balance:" + accountId,
                account.getBalance().toString(),
                5,
                TimeUnit.MINUTES
        );
        return account.getBalance();
    }

    @Override
    public AccountDTOs.AccountBalanceResponse getBalanceInfo(String id) {
        Account account = getAccountById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        BigDecimal balance = getBalance(id);

        return AccountDTOs.AccountBalanceResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(balance)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Account updateBalance(String accountId, BigDecimal newBalance) {
        Account account = getAccountById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOperationException("Balance cannot be negative");
        }

        account.setBalance(newBalance);
        Account updatedAccount = accountRepository.save(account);

        // update cache
        redisTemplate.opsForValue().set(
                "balance:" + accountId,
                newBalance.toString(),
                5,
                TimeUnit.MINUTES
        );
        log.info("Updated account {} balance to {}", account.getAccountNumber(), newBalance);
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
