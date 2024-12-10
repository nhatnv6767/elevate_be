package com.elevatebanking.service.nonImp;

import com.elevatebanking.exception.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;

    private static final String TOKEN_PREFIX = "pwd_reset:";
    private static final String ATTEMPT_PREFIX = "reset_attempt:";
    private static final int MAX_ATTEMPTS = 100;
    private static final long TOKEN_TTL = 30;
    private static final long ATTEMPT_TTL = 15;

    public void processForgotPassword(String email) {
        validateResetAttempts(email);
        String token = createResetToken(email);
        emailService.sendResetPasswordEmail(email, token);
    }

    private void validateResetAttempts(String email) {
        String attemptKey = ATTEMPT_PREFIX + email;
        Integer attempts = Optional.ofNullable(
                redisTemplate.opsForValue().get(attemptKey)).map(Integer::parseInt).orElse(0);

        if (attempts >= MAX_ATTEMPTS) {
            throw new TooManyAttemptsException("Too many reset attempts. Please try again after 15 minutes");
        }
        redisTemplate.opsForValue().increment(attemptKey);
        redisTemplate.expire(attemptKey, ATTEMPT_TTL, TimeUnit.MINUTES);
    }

    private String createResetToken(String email) {
        String token = UUID.randomUUID().toString();
        String key = TOKEN_PREFIX + token;

        redisTemplate.opsForValue().set(
                key,
                email,
                TOKEN_TTL,
                TimeUnit.MINUTES);

        return token;
    }

    public Optional<String> validateToken(String token) {
        String key = TOKEN_PREFIX + token;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void invalidateToken(String token) {
        String key = TOKEN_PREFIX + token;
        redisTemplate.delete(key);
    }
}
