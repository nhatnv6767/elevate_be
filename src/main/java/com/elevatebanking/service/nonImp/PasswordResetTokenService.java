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
    private static final int MAX_ATTEMPTS = 100;
    private static final long TOKEN_TTL = 30;
    private static final long ATTEMPT_TTL = 15;

    public void processForgotPassword(String email) {
        validateResetAttempts(email);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String token = createResetToken(email);
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
