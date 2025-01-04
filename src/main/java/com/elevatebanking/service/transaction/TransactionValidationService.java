package com.elevatebanking.service.transaction;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.UserTier;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.TransactionLimitExceededException;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.service.transaction.config.TransactionLimitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionValidationService {
    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final TransactionLimitConfig limitConfig;

    private static final int MAX_TRANSACTIONS_PER_DAY = 20;
    private static final int MAX_TRANSACTIONS_PER_MINUTE = 3;
    private static final BigDecimal MIN_TRANSFER_AMOUNT = new BigDecimal("0.1"); // 0.1$
    private static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal(5000000); // 5,000,000$
    private static final BigDecimal SINGLE_TRANSFER_LIMIT = new BigDecimal(1000000); // 1,000,000$
    

    public void validateTransferTransaction(Account fromAccount, Account toAccount, BigDecimal amount) {
        validateBasicRules(fromAccount, toAccount, amount);
        validateLimits(fromAccount, amount);
    }

    private void validateBasicRules(Account fromAccount, Account toAccount, BigDecimal amount) {
        validateAccountStatus(fromAccount);
        validateAccountStatus(toAccount);
        validateTransactionAmount(amount);
        validateSufficientBalance(fromAccount, amount);
        validateSameAccount(fromAccount, toAccount);
    }

    private void validateLimits(Account account, BigDecimal amount) {
        String userId = account.getUser().getId();
        TransactionLimitConfig.TierLimit limits = getLimitsForUser(account.getUser());

        validateSingleTransactionLimit(amount, limits);
        validateDailyLimit(userId, amount, limits);
        validateMonthlyLimit(userId, amount, limits);
        validateTransactionFrequency(userId, limits);
    }

    private void validateSingleTransactionLimit(BigDecimal amount, TransactionLimitConfig.TierLimit limits) {
        if (amount.compareTo(limits.getSingleTransactionLimit()) > 0) {
            throw new TransactionLimitExceededException(
                    String.format("Transaction amount exceeds single transaction limit of %s",
                            limits.getSingleTransactionLimit())
            );
        }
    }


    private void validateSameAccount(Account fromAccount, Account toAccount) {
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new InvalidOperationException("Cannot transfer to the same account");
        }
    }

    private void validateSufficientBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InvalidOperationException("Insufficient balance");
        }
    }

    private void validateTransactionAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationException("Amount must be greater than 0");
        }

        if (amount.compareTo(MIN_TRANSFER_AMOUNT) < 0) {
            throw new InvalidOperationException(String.format("Amount must be at least %s", MIN_TRANSFER_AMOUNT));
        }

        if (amount.compareTo(SINGLE_TRANSFER_LIMIT) > 0) {
            throw new InvalidOperationException(
                    String.format("Transaction amount exceeds limit of %s", SINGLE_TRANSFER_LIMIT)
            );
        }
    }

    private void validateAccountStatus(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidOperationException(
                    String.format("Account %s is not active", account.getAccountNumber())
            );
        }
    }


    private BigDecimal calculateDailyTotal(String userId) {
        try {
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            List<Transaction> dailyTransactions = transactionRepository
                    .findTransactionsByUserAndDateRange(userId, startOfDay, LocalDateTime.now());

            return dailyTransactions.stream()
                    .filter(transaction -> transaction.getStatus() == TransactionStatus.COMPLETED)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.error("Error calculating daily total for user {}: {}", userId, e.getMessage());
            throw new InvalidOperationException("Could not calculate daily transaction total");
        }
    }

    private BigDecimal calculateMonthlyTotal(String userId) {
        try {
            LocalDateTime startOfMonth = LocalDateTime.now()
                    .withDayOfMonth(1)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0);
            List<Transaction> monthlyTransactions = transactionRepository
                    .findTransactionsByUserAndDateRange(userId, startOfMonth, LocalDateTime.now());
            return monthlyTransactions.stream()
                    .filter(transaction -> transaction.getStatus() == TransactionStatus.COMPLETED)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.error("Error calculating monthly total for user {}: {}", userId, e.getMessage());
            throw new InvalidOperationException("Could not calculate monthly transaction total");
        }
    }

    private void validateDailyLimit(String userId, BigDecimal amount, TransactionLimitConfig.TierLimit limits) {
        String cacheKey = "daily_total:" + userId;
        BigDecimal dailyTotal = getCachedOrCalculateTotal(
                cacheKey,
                () -> calculateDailyTotal(userId),
                1,
                TimeUnit.DAYS
        );

        if (dailyTotal.add(amount).compareTo(limits.getDailyLimit()) > 0) {
            throw new TransactionLimitExceededException(
                    String.format("Daily transfer limit exceeded. Current limit: %s", limits.getDailyLimit())
            );
        }
    }

    private void validateMonthlyLimit(String userId, BigDecimal amount, TransactionLimitConfig.TierLimit limits) {
        String cacheKey = "monthly_total:" + userId;
        BigDecimal monthlyTotal = getCachedOrCalculateTotal(
                cacheKey,
                () -> calculateMonthlyTotal(userId),
                30,
                TimeUnit.DAYS
        );

        if (monthlyTotal.add(amount).compareTo(limits.getMonthlyLimit()) > 0) {
            throw new TransactionLimitExceededException(
                    String.format("Monthly transfer limit exceeded. Current limit: %s", limits.getMonthlyLimit())
            );
        }
    }


    private BigDecimal getCachedOrCalculateTotal(String cacheKey, Supplier<BigDecimal> calculator,
                                                 long duration, TimeUnit timeUnit
    ) {
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            return new BigDecimal(cachedValue);
        }
        BigDecimal calculated = calculator.get();
        redisTemplate.opsForValue().set(cacheKey, calculated.toString(), duration, timeUnit);
        return calculated;
    }

    private void validateTransactionFrequency(String userId, TransactionLimitConfig.TierLimit limits) {
        String minuteKey = "tx_count_minute:" + userId;
        String dayKey = "tx_count_day:" + userId;
        Long txPerMinute = incrementAndGetCount(minuteKey, 1, TimeUnit.MINUTES);
        if (txPerMinute > limits.getMaxTransactionsPerMinute()) {
            throw new TransactionLimitExceededException(
                    String.format("Exceeded maximum transaction per minute of %d", limits.getMaxTransactionsPerMinute())
            );
        }
        Long txPerDay = incrementAndGetCount(dayKey, 1, TimeUnit.DAYS);
        if (txPerDay > limits.getMaxTransactionsPerDay()) {
            throw new TransactionLimitExceededException(
                    String.format("Exceeded maximum transaction per day of %d", limits.getMaxTransactionsPerDay())
            );
        }
    }

    private Long incrementAndGetCount(String key, long duration, TimeUnit timeUnit) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.error("Failed to increment key: {}", key);
                throw new InvalidOperationException("Failed to increment key");
            }
            // set the expiration if it's the first increment
            Boolean expireResult = redisTemplate.expire(key, duration, timeUnit);
            if (expireResult == null || !expireResult) {
                log.warn("Failed to set expiration for key: {}", key);
                // try to delete the key
                redisTemplate.delete(key);
                throw new InvalidOperationException("Failed to validate transaction frequency");
            }
            return count;

        } catch (Exception e) {
            log.error("Error while validating transaction frequency: {}", e.getMessage());
            // clean up the key in case of an error
            try {
                redisTemplate.delete(key);
            } catch (Exception cleanupEx) {
                log.error("Failed to cleanup key after error: {}", cleanupEx.getMessage());
            }
            throw new InvalidOperationException("Failed to validate transaction frequency");
        }
    }

    private TransactionLimitConfig.TierLimit getLimitsForUser(User user) {
        UserTier tier = user.getTier();
        if (tier == null) {
            tier = UserTier.BASIC;
        }
        return limitConfig.getTiers()
                .getOrDefault(tier.name().toLowerCase(), getDefaultLimits());
    }

    private TransactionLimitConfig.TierLimit getDefaultLimits() {
        return TransactionLimitConfig.TierLimit.builder()
                .singleTransactionLimit(new BigDecimal("1000000"))
                .dailyLimit(new BigDecimal("5000000"))
                .monthlyLimit(new BigDecimal("50000000"))
                .maxTransactionsPerMinute(3)
                .maxTransactionsPerDay(20)
                .build();
    }
}
