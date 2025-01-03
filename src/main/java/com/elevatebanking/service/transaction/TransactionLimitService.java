package com.elevatebanking.service.transaction;

import com.elevatebanking.entity.enums.TransactionType;
import com.elevatebanking.entity.limit.LimitException;
import com.elevatebanking.entity.limit.LimitHistory;
import com.elevatebanking.entity.limit.TransactionLimit;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.TransactionLimitExceededException;
import com.elevatebanking.repository.LimitExceptionRepository;
import com.elevatebanking.repository.LimitHistoryRepository;
import com.elevatebanking.repository.TransactionLimitRepository;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.util.SecurityUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TransactionLimitService {
    TransactionLimitRepository limitRepository;
    LimitHistoryRepository historyRepository;
    LimitExceptionRepository exceptionRepository;
    TransactionRepository transactionRepository;
    RedisTemplate<String, String> redisTemplate;

    @Transactional(readOnly = true)
    public TransactionLimit getUserLimit(String userId) {
        return limitRepository.findActiveByUserId(userId)
                .orElseGet(() -> createDefaultLimit(userId));
    }

    @Transactional
    public void validateTransactionLimit(String userId, BigDecimal amount, TransactionType type) {
        TransactionLimit limit = getUserLimit(userId);

        // check if there is an active exception
        Optional<LimitException> activeException = exceptionRepository.findActiveExceptionForUser(userId);
        if (activeException.isPresent()) {
            validateWithException(activeException.get(), amount);
            return;
        }

        // validate transaction type
        if (!limit.getAllowedTransactionTypes().contains(type)) {
            throw new InvalidOperationException("Transaction type not allowed:  " + type);
        }

        // validate single tracsaction limit
        if (amount.compareTo(limit.getSingleTransactionLimit()) > 0) {
            throw new TransactionLimitExceededException(
                    "Amount exceeds single transaction limit of " + limit.getSingleTransactionLimit()
            );
        }

        // validate daily limit
        validateDailyLimit(userId, amount, limit.getDailyLimit());

        // validate monthly limit
        validateMonthlyLimit(userId, amount, limit.getMonthlyLimit());

        validateTransactionFrequency(userId, limit);
    }

    @Transactional
    public TransactionLimit updateLimit(String userId, TransactionLimit newLimit) {
        TransactionLimit currentLimit = getUserLimit(userId);
        // create history record
        createLimitHistory(currentLimit, "UPDATE", currentLimit.toString(), newLimit.toString());
        // update fields
        updateLimitFields(currentLimit, newLimit);
        return limitRepository.save(currentLimit);
    }

    @Transactional
    public LimitException createException(LimitException exception) {
        validateException(exception);
        return exceptionRepository.save(exception);
    }

    void validateException(LimitException exception) {
        if (exception.getStartTime().isAfter(exception.getEndTime())) {
            throw new InvalidOperationException("Start time cannot be after end time");
        }
        if (exception.getEndTime().isBefore(LocalDateTime.now())) {
            throw new InvalidOperationException("End time cannot be in the past");
        }
        if (exception.getExceptionLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOperationException("Exception limit must be greater than zero");
        }
    }

    void updateLimitFields(TransactionLimit current, TransactionLimit updated) {
        if (updated.getSingleTransactionLimit() != null) {
            current.setSingleTransactionLimit(updated.getSingleTransactionLimit());
        }
        if (updated.getDailyLimit() != null) {
            current.setDailyLimit(updated.getDailyLimit());
        }

        if (updated.getMonthlyLimit() != null) {
            current.setMonthlyLimit(updated.getMonthlyLimit());
        }

        if (updated.getMaxTransactionsPerMinute() > 0) {
            current.setMaxTransactionsPerMinute(updated.getMaxTransactionsPerMinute());
        }

        if (updated.getMaxTransactionsPerDay() > 0) {
            current.setMaxTransactionsPerDay(updated.getMaxTransactionsPerDay());
        }

        if (updated.getAllowedTransactionTypes() != null) {
            current.setAllowedTransactionTypes(updated.getAllowedTransactionTypes());
        }
    }

    void validateTransactionFrequency(String userId, TransactionLimit limit) {
        String minuteKey = "tx_count_minute:" + userId;
        String dayKey = "tx_count_day:" + userId;

        // check transactions per minute

        Long txPerMinute = redisTemplate.opsForValue().increment(minuteKey, 1L);
        if (txPerMinute == null) {
            throw new TransactionLimitExceededException("Redis unavailable");
        }
        if (txPerMinute == 1L) {
            redisTemplate.expire(minuteKey, 1, TimeUnit.MINUTES);
        }

        if (txPerMinute > limit.getMaxTransactionsPerMinute()) {
            throw new TransactionLimitExceededException(
                    "Exceeded maximum transactions per minute of " + limit.getMaxTransactionsPerMinute()
            );
        }

        // check transactions per day
        Long txPerDay = redisTemplate.opsForValue().increment(dayKey, 1L);
        if (txPerDay == null) {
            throw new TransactionLimitExceededException("Redis unavailable");
        }
        if (txPerDay == 1L) {
            redisTemplate.expire(dayKey, 1, TimeUnit.DAYS);
        }
        if (txPerDay > limit.getMaxTransactionsPerDay()) {
            throw new TransactionLimitExceededException(
                    "Exceeded maximum transactions per day of " + limit.getMaxTransactionsPerDay()
            );
        }

    }

    void validateMonthlyLimit(String userId, BigDecimal amount, BigDecimal limit) {
        String cacheKey = "monthly_total:" + userId;
        // try to get from cache first
        String cachedTotal = redisTemplate.opsForValue().get(cacheKey);
        BigDecimal monthlyTotal;

        if (cachedTotal != null) {
            monthlyTotal = new BigDecimal(cachedTotal);
        } else {
            // if not in cache , calculate from db
            LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            monthlyTotal = transactionRepository
                    .findTransactionsByAccountAndDateRange(userId, startOfMonth, LocalDateTime.now())
                    .stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
            ;
            // cache the result
            redisTemplate.opsForValue().set(cacheKey, monthlyTotal.toString(), 30, TimeUnit.DAYS);
        }

        if (monthlyTotal.add(amount).compareTo(limit) > 0) {
            throw new TransactionLimitExceededException(
                    "Transaction would exceed monthly limit of " + limit
            );
        }
    }

    void validateDailyLimit(String userId, BigDecimal amount, BigDecimal limit) {
        String cacheKey = "daily_total:" + userId;
        // try to get from cache first
        String cachedTotal = redisTemplate.opsForValue().get(cacheKey);
        BigDecimal dailyTotal;

        if (cachedTotal != null) {
            dailyTotal = new BigDecimal(cachedTotal);
        } else {
            // if not in cache , calculate from db
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            dailyTotal = transactionRepository
                    .findTransactionsByAccountAndDateRange(userId, startOfDay, LocalDateTime.now())
                    .stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
            ;
            // cache the result
            redisTemplate.opsForValue().set(cacheKey, dailyTotal.toString(), 1, TimeUnit.DAYS);
        }

        if (dailyTotal.add(amount).compareTo(limit) > 0) {
            throw new TransactionLimitExceededException(
                    "Transaction would exceed daily limit of " + limit
            );
        }
    }

    void validateWithException(LimitException exception, BigDecimal amount) {
        if (amount.compareTo(exception.getExceptionLimit()) > 0) {
            throw new TransactionLimitExceededException(
                    "Amount exceeds exception limit of " + exception.getExceptionLimit()
            );
        }
    }

    TransactionLimit createDefaultLimit(String userId) {
        TransactionLimit limit = new TransactionLimit();
        limit.setId(userId);
        limit.setSingleTransactionLimit(new BigDecimal("1000000"));
        limit.setDailyLimit(new BigDecimal("5000000"));
        limit.setMonthlyLimit(new BigDecimal("50000000"));
        limit.setMaxTransactionsPerMinute(3);
        limit.setMaxTransactionsPerDay(20);

        createLimitHistory(limit, "CREATE", null, limit.toString());
        return limitRepository.save(limit);

    }

    void createLimitHistory(TransactionLimit limit, String action, String oldValue, String newValue) {
        LimitHistory history = new LimitHistory();
        history.setTransactionLimit(limit);
        history.setChangedField(action);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setChangedBy(SecurityUtils.getCurrentUserId());
        history.setChangeReason("System update");
        historyRepository.save(history);

    }

    // audit and monitoring methods
    public List<LimitHistory> getLimitHistory(String userId) {
        return historyRepository.findUserLimitHistory(userId);
    }

    public List<LimitException> getActiveExceptions(String userId) {
        return exceptionRepository.findByUserId(userId)
                .stream()
                .filter(e -> e.isActive() && e.getEndTime().isAfter(LocalDateTime.now()))
                .collect(Collectors.toList());
    }

    public void refreshLimitCache(String userId) {
        String dailyKey = "daily_total:" + userId;
        String monthlyKey = "monthly_total:" + userId;
        redisTemplate.delete(dailyKey);
        redisTemplate.delete(monthlyKey);

        // force recalculation
        TransactionLimit limit = getUserLimit(userId);
        validateDailyLimit(userId, BigDecimal.ZERO, limit.getDailyLimit());
        validateMonthlyLimit(userId, BigDecimal.ZERO, limit.getMonthlyLimit());
    }
}
