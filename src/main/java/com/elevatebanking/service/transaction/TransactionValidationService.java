package com.elevatebanking.service.transaction;

import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.enums.AccountStatus;
import com.elevatebanking.entity.enums.TransactionStatus;
import com.elevatebanking.entity.enums.UserTier;
import com.elevatebanking.entity.transaction.Transaction;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.event.NotificationEvent;
import com.elevatebanking.exception.InvalidOperationException;
import com.elevatebanking.exception.TransactionLimitExceededException;
import com.elevatebanking.exception.TransactionProcessingException;
import com.elevatebanking.repository.TransactionRepository;
import com.elevatebanking.service.notification.NotificationService;
import com.elevatebanking.service.transaction.config.TransactionLimitConfig;
import io.lettuce.core.RedisConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionValidationService {
    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final TransactionLimitConfig limitConfig;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Value("${spring.data.redis.retry.initial-interval}")
    private long initialInterval;
    @Value("${spring.data.redis.retry.max-attempts}")
    private int maxRetries;
    @Value("${spring.data.redis.retry.multiplier}")
    private double multiplier;

    private static final String KEY_PREFIX = "tx_count:";


    private static final int MAX_TRANSACTIONS_PER_DAY = 20;
    private static final int MAX_TRANSACTIONS_PER_MINUTE = 3;
    private static final BigDecimal MIN_TRANSFER_AMOUNT = new BigDecimal("0.1"); // 0.1$
    private static final BigDecimal DAILY_TRANSFER_LIMIT = new BigDecimal(5000000); // 5,000,000$
    private static final BigDecimal SINGLE_TRANSFER_LIMIT = new BigDecimal(1000000); // 1,000,000$


    public void validateTransferTransaction(Account fromAccount, Account toAccount, BigDecimal amount) throws InterruptedException {
        validateBasicRules(fromAccount, toAccount, amount);
        validateLimits(fromAccount, amount);
    }

    public void validateWithdrawalTransaction(Account account, BigDecimal amount) throws InterruptedException {
        validateBasicRules(account, account, amount);
        validateLimits(account, amount);
    }

    public void validateDepositTransaction(Account account, BigDecimal amount) throws InterruptedException {
        validateBasicRules(account, account, amount);
        validateLimits(account, amount);
    }


    private void validateBasicRules(Account fromAccount, Account toAccount, BigDecimal amount) {
        validateAccountStatus(fromAccount);
        validateAccountStatus(toAccount);
        validateTransactionAmount(amount);
        validateSufficientBalance(fromAccount, amount);
        validateSameAccount(fromAccount, toAccount);
    }

    private void validateLimits(Account account, BigDecimal amount) throws InterruptedException {
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

    private void validateTransactionFrequency(String userId, TransactionLimitConfig.TierLimit limits) throws InterruptedException {
        String lockKey = "lock:" + userId;
        boolean locked = false;
        try {
            String minuteKey = new StringBuilder("tx_count_minute:")
                    .append(userId)
                    .toString();
            String dayKey = new StringBuilder("tx_count_day:")
                    .append(userId)
                    .toString();
            locked = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS));

            if (!locked) {
                throw new InvalidOperationException("Failed to acquire lock for transaction frequency validation");
            }
//            String minuteKey = "tx_count_minute:" + userId;
//            String dayKey = "tx_count_day:" + userId;
            try {
                Long txPerMinute = incrementAndGetCountWithRetry(minuteKey, 1, TimeUnit.MINUTES);
                validateLimit(txPerMinute, (long) limits.getMaxTransactionsPerMinute(), "minute");

                Long txPerDay = incrementAndGetCountWithRetry(dayKey, 1, TimeUnit.DAYS);
                validateLimit(txPerDay, (long) limits.getMaxTransactionsPerDay(), "day");
            } catch (Exception e) {
                log.error("Error validating transaction frequency", e);
                throw new InvalidOperationException("Failed to validate transaction frequency");
            } finally {
                if (locked) {
                    redisTemplate.delete(lockKey);
                }
            }

        } catch (Exception e) {
            if (e instanceof InvalidOperationException) {
                throw e;
            }
            log.error("Error validating transaction frequency", e);
            throw new InvalidOperationException("Failed to validate transaction frequency");
        }
    }

    private void validateLimit(Long current, Long max, String period) {
        if (current > max) {
            throw new TransactionLimitExceededException(
                    String.format("Exceeded maximum transaction per %s of %d", period, max)
            );
        }
    }

    private Long incrementAndGetCountWithRetry(String key, long duration, TimeUnit timeUnit) throws InterruptedException {
        long retryDelay = 1000; // 1 second initial delay

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (!isRedisAvailable()) {
                    throw new RedisConnectionException("Redis is not available");
                }

                return incrementAndGetCount(key, duration, timeUnit);

            } catch (RedisConnectionException e) {
                log.warn("Redis connection failed on attempt {}/{}", attempt + 1, maxRetries);
                // Exponential backoff
                Thread.sleep(retryDelay * (long) Math.pow(2, attempt));

            } catch (Exception e) {
                log.error("Unexpected error during increment: {}", e.getMessage());
                throw new TransactionProcessingException("Failed to process transaction", null, false);
            }
        }

        // Fallback to database
        return handleWithDatabaseFallback(key);
    }

    private Long incrementAndGetCount(String key, long duration, TimeUnit timeUnit) {
        try {
            // Thực hiện increment và set expire trong một transaction
            return redisTemplate.execute(new SessionCallback<Long>() {
                @Override
                public Long execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    Long count = operations.opsForValue().increment(key);
                    operations.expire(key, duration, timeUnit);

                    List<Object> results = operations.exec();
                    if (results == null || results.isEmpty()) {
                        throw new InvalidOperationException("Error processing Redis transaction");
                    }

                    return (Long) results.get(0);
                }
            });
        } catch (Exception e) {
            log.error("Error incrementing count: key={}, error={}", key, e.getMessage());
            throw new TransactionProcessingException("Failed to process transaction", null, false);
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

    // REDIS HEALTH MONITORING

    private String extractUserIdFromKey(String key) {
        // Key format: "tx_count:userId"
        if (key != null && key.startsWith(KEY_PREFIX)) {
            return key.substring(KEY_PREFIX.length());
        }
        throw new IllegalArgumentException("Invalid key format");
    }


    private Long handleWithDatabaseFallback(String key) {
        try {
            String userId = extractUserIdFromKey(key);
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);

            // Sử dụng native query trong repository
            return transactionRepository.countTransactionsByUserInTimeRange(
                    userId,
                    startTime,
                    LocalDateTime.now()
            ) + 1L;
        } catch (Exception e) {
            log.error("Database fallback failed for key: {}", key, e);
            throw new TransactionProcessingException("Failed to validate transaction frequency", null, false);
        }
    }

    private void logTransactionValidationAttempt(String userId, BigDecimal amount, int attempt) {
        log.info(
                "Validating transaction - UserId: {}, Amount: {}, Attempt: {}/{}",
                userId, amount, attempt, maxRetries
        );
    }

    private boolean isRedisAvailable() {
        long startTime = System.currentTimeMillis();
        try {
            // Create a RedisCallback that will execute our Redis command
            RedisCallback<String> pingCallback = connection -> {
                try {
                    // Get the raw Redis connection and execute PING command
                    // Converting the returned byte[] to String
                    return new String(connection.ping().getBytes(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.error("Error executing Redis PING command: {}", e.getMessage());
                    return null;
                }
            };

            // Execute the callback using RedisTemplate
            String result = redisTemplate.execute(pingCallback);
            long responseTime = System.currentTimeMillis() - startTime;
            log.debug("Redis health check completed in {}ms", responseTime);

            // Redis responds with "PONG" if the connection is alive
            return "PONG".equals(result);

        } catch (Exception e) {
            long failureTime = System.currentTimeMillis() - startTime;
            log.error("Redis health check failed after {}ms: {}", failureTime, e.getMessage());
            return false;
        }
    }

    @Scheduled(fixedRate = 60000)
    public void monitorRedisHealth() {
        boolean isAvailable = isRedisAvailable();
        if (!isAvailable) {
            String message = "Redis connection is not available - Transaction validation may be affected";
            log.error(message);

            // Tạo notification event cho system alert
            NotificationEvent systemAlert = NotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .type(NotificationEvent.NotificationType.SYSTEM_NOTIFICATION.name())
                    .priority(NotificationEvent.Priority.HIGH.name())
                    .title("System Alert: Redis Health Check Failed")
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .metadata(Map.of(
                            "component", "Redis",
                            "status", "DOWN",
                            "impact", "Transaction Validation"
                    ))
                    .build();

            // Gửi notification qua Kafka
            kafkaTemplate.send("elevate.notifications", systemAlert.getEventId(), systemAlert)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send Redis health alert: {}", ex.getMessage());
                        } else {
                            log.info("Redis health alert sent successfully");
                        }
                    });
        }
    }
}
