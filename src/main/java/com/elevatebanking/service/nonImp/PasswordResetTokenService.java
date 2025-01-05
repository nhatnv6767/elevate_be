package com.elevatebanking.service.nonImp;

import com.elevatebanking.entity.user.User;
import com.elevatebanking.event.EmailEvent;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.exception.TooManyAttemptsException;
import com.elevatebanking.repository.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, EmailEvent> kafkaTemplate;

    private static final String TOKEN_PREFIX = "pwd_reset:";
    private static final String ATTEMPT_PREFIX = "reset_attempt:";
    private static final String EMAIL_COOLDOWN_PREFIX = "email_cooldown:";
    private static final String LOCK_PREFIX = "lock:reset:";
    private static final int MAX_ATTEMPTS = 5; // Số lần thử tối đa trong khoảng thời gian
    private static final long ATTEMPT_TTL = 15; // Thời gian reset số lần thử (phút)
    private static final long EMAIL_COOLDOWN = 2; // Thời gian chờ giữa các request (phút)
    private static final long TOKEN_TTL = 30; // Thời gian token hết hạn (phút)
    private static final long LOCK_TIMEOUT = 10; // seconds

    public void processForgotPassword(String email) {
        validateResetAttempts(email);
        checkEmailCooldown(email);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String token = createResetToken(email);
        setEmailCooldown(email);
//        emailService.sendResetPasswordEmail(email, token, user.getUsername());
        EmailEvent emailEvent = EmailEvent.passwordResetEvent(email, user.getUsername(), token).build();

        kafkaTemplate.send("elevate.emails", emailEvent)
                .whenComplete((result, ex) -> {
                            if (ex != null) {
                                throw new RuntimeException("Failed to send email", ex);
                            }
                        }

                );
    }

    private void validateResetAttempts(String email) {
        String attemptKey = ATTEMPT_PREFIX + email;
        Integer attempts = Optional.ofNullable(
                redisTemplate.opsForValue().get(attemptKey)
        ).map(Integer::parseInt).orElse(0);
        if (attempts >= MAX_ATTEMPTS) {
            throw new TooManyAttemptsException("Too many reset attempts. Please try again after " + ATTEMPT_TTL + " minutes");
        }
        redisTemplate.opsForValue().increment(attemptKey);
        redisTemplate.expire(attemptKey, ATTEMPT_TTL, TimeUnit.MINUTES);
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
