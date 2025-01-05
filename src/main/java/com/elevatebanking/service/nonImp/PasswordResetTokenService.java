package com.elevatebanking.service.nonImp;

import com.elevatebanking.entity.user.User;
import com.elevatebanking.event.EmailEvent;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.exception.TooManyAttemptsException;
import com.elevatebanking.repository.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetTokenService {
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;

    private static final String TOKEN_PREFIX = "pwd_reset:";
    private static final String ATTEMPT_PREFIX = "reset_attempt:";
    private static final String EMAIL_COOLDOWN_PREFIX = "email_cooldown:";
    private static final String LOCK_PREFIX = "lock:reset:";
    private static final String EMAIL_TOKEN_PREFIX = "pwd_reset_email:";
    private static final int MAX_ATTEMPTS = 5; // Số lần thử tối đa trong khoảng thời gian
    private static final long ATTEMPT_TTL = 15; // Thời gian reset số lần thử (phút)
    private static final long EMAIL_COOLDOWN = 2; // Thời gian chờ giữa các request (phút)
    private static final long TOKEN_TTL = 30; // Thời gian token hết hạn (phút)
    private static final long LOCK_TIMEOUT = 10; // seconds

    public void processForgotPassword(String email) {
        String lockKey = LOCK_PREFIX + email;
        int maxRetries = 3;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                log.info("Attempting to acquire lock for email: {}", email);
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", LOCK_TIMEOUT, TimeUnit.SECONDS);

                if (Boolean.FALSE.equals(acquired)) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        throw new RuntimeException("Request is being processed. Please try again later");
                    }
                    Thread.sleep(100); // Wait 100ms before retry
                    continue;
                }
                log.info("Lock acquired for email: {}", email);

                validateResetAttempts(email);
                log.info("Attempt validation passed for email: {}", email);
                checkEmailCooldown(email);
                log.info("Cooldown check passed for email: {}", email);
                User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
                String token = createResetToken(email);
                setEmailCooldown(email);

                redisTemplate.execute(new SessionCallback<List<Object>>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<Object> execute(RedisOperations operations) throws DataAccessException {
                        operations.multi();

                        String attemptKey = ATTEMPT_PREFIX + email;
                        operations.opsForValue().increment(attemptKey);
                        operations.expire(attemptKey, ATTEMPT_TTL, TimeUnit.MINUTES);

                        String cooldownKey = EMAIL_COOLDOWN_PREFIX + email;
                        operations.opsForValue().set(cooldownKey, "1", EMAIL_COOLDOWN, TimeUnit.MINUTES);
                        return operations.exec();
                    }
                });

                EmailEvent emailEvent = EmailEvent.passwordResetEvent(email, user.getUsername(), token).build();
                kafkaTemplate.send("elevate.emails", emailEvent)
                        .whenComplete((result, ex) -> {
                                    if (ex != null) {
                                        throw new RuntimeException("Failed to send email", ex);
                                    }
                                }

                        );
                break;
            } catch (ResourceNotFoundException e) {
                log.error("Error processing password reset for email: {}", email, e);
                throw new ResourceNotFoundException("User not found with email: " + email);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                log.info("Releasing lock for email: {}", email);
                redisTemplate.delete(lockKey);
            }
        }

    }

    private void validateResetAttempts(String email) {
        String attemptKey = ATTEMPT_PREFIX + email;
        Integer attempts = Optional.ofNullable(
                redisTemplate.opsForValue().get(attemptKey)
        ).map(Integer::parseInt).orElse(0);
        if (attempts >= MAX_ATTEMPTS) {
            throw new TooManyAttemptsException("Too many reset attempts. Please try again after " + ATTEMPT_TTL + " minutes");
        }
    }


    private void checkEmailCooldown(String email) {
        String cooldownKey = EMAIL_COOLDOWN_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            Long ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0) {
                throw new RuntimeException("Please wait " + ttl + " seconds before trying again");
            }
        }
    }

    private void setEmailCooldown(String email) {
        String cooldownKey = EMAIL_COOLDOWN_PREFIX + email;
        redisTemplate.opsForValue().set(cooldownKey, "1", EMAIL_COOLDOWN, TimeUnit.MINUTES);
    }


    private String createResetToken(String email) {
        String token = UUID.randomUUID().toString();
        String tokenKey = TOKEN_PREFIX + token;
        String emailKey = EMAIL_TOKEN_PREFIX + email;

        String oldToken = redisTemplate.opsForValue().get(emailKey);
        if (oldToken != null) {
            redisTemplate.delete(TOKEN_PREFIX + oldToken);
        }

        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForValue().set(
                        tokenKey,
                        email,
                        TOKEN_TTL,
                        TimeUnit.MINUTES);
                operations.opsForValue().set(emailKey, token, TOKEN_TTL, TimeUnit.MINUTES);
                return operations.exec();
            }
        });

        return token;
    }

    public Optional<String> validateToken(String token) {
        String key = TOKEN_PREFIX + token;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void invalidateToken(String token) {
        String key = TOKEN_PREFIX + token;
        String email = redisTemplate.opsForValue().get(key);
        if (email != null) {
            String emailKey = EMAIL_TOKEN_PREFIX + email;
            redisTemplate.delete(emailKey);
        }
        redisTemplate.delete(key);
    }

}
