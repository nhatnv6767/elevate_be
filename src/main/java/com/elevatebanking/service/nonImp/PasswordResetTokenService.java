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

    // Các prefix key được sử dụng trong Redis
    private static final String TOKEN_PREFIX = "pwd_reset:"; // Lưu token reset password
    private static final String ATTEMPT_PREFIX = "reset_attempt:"; // Lưu số lần thử reset
    private static final String EMAIL_COOLDOWN_PREFIX = "email_cooldown:"; // Lưu thời gian chờ giữa các lần gửi email
    private static final String LOCK_PREFIX = "lock:reset:"; // Lock để tránh xử lý đồng thời
    private static final String EMAIL_TOKEN_PREFIX = "pwd_reset_email:"; // Map email với token

    // Các thông số cấu hình
    private static final int MAX_ATTEMPTS = 5; // Số lần thử tối đa trong khoảng thời gian
    private static final long ATTEMPT_TTL = 15; // Thời gian reset số lần thử (phút)
    private static final long EMAIL_COOLDOWN = 2; // Thời gian chờ giữa các request (phút)
    private static final long TOKEN_TTL = 30; // Thời gian token hết hạn (phút)
    private static final long LOCK_TIMEOUT = 10; // Thời gian timeout của lock (giây)

    public void processForgotPassword(String email) {
        String lockKey = LOCK_PREFIX + email;
        int maxRetries = 3;
        int retryCount = 0;

        // Thử acquire lock tối đa 3 lần để tránh xử lý đồng thời cho cùng 1 email
        while (retryCount < maxRetries) {
            try {
                log.info("Trying to acquire lock for email: {}", email);

                // Thử set key vào Redis với timeout 10s nếu key chưa tồn tại
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", LOCK_TIMEOUT, TimeUnit.SECONDS);

                // Nếu không lấy được lock (key đã tồn tại)
                if (Boolean.FALSE.equals(acquired)) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        // Nếu đã thử quá 3 lần thì báo lỗi
                        throw new RuntimeException("Request is being processed. Please try again later");
                    }
                    // Đợi 100ms rồi thử lại
                    Thread.sleep(100);
                    continue;
                }
                log.info("Lock acquired for email: {}", email);

                // Kiểm tra số lần thử reset password và thời gian chờ giữa các lần gửi email
                validateResetAttempts(email);
                log.info("Passed attempt validation for email: {}", email);
                checkEmailCooldown(email);
                log.info("Passed cooldown validation for email: {}", email);

                // Tìm user theo email, throw exception nếu không tìm thấy
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                // Tạo token reset password mới
                String token = createResetToken(email);

                // Set thời gian chờ cho lần gửi email tiếp theo
                setEmailCooldown(email);

                // Cập nhật số lần thử và cooldown trong 1 transaction Redis
                redisTemplate.execute(new SessionCallback<List<Object>>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<Object> execute(RedisOperations operations) throws DataAccessException {
                        operations.multi();

                        // Tăng số lần thử và set TTL 15 phút
                        String attemptKey = ATTEMPT_PREFIX + email;
                        operations.opsForValue().increment(attemptKey);
                        operations.expire(attemptKey, ATTEMPT_TTL, TimeUnit.MINUTES);

                        // Set cooldown 2 phút cho email
                        String cooldownKey = EMAIL_COOLDOWN_PREFIX + email;
                        operations.opsForValue().set(cooldownKey, "1", EMAIL_COOLDOWN, TimeUnit.MINUTES);
                        return operations.exec();
                    }
                });

                // Gửi email reset password qua Kafka
                EmailEvent emailEvent = EmailEvent.passwordResetEvent(email, user.getUsername(), token).build();
                kafkaTemplate.send("elevate.emails", emailEvent)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                throw new RuntimeException("Unable to send email", ex);
                            }
                        });
                break;
            } catch (ResourceNotFoundException e) {
                log.error("Error processing password reset for email: {}", email, e);
                throw new ResourceNotFoundException("User not found with email: " + email);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                // Luôn xóa lock khi xử lý xong
                log.info("Removing lock for email: {}", email);
                redisTemplate.delete(lockKey);
            }
        }
    }

    // Kiểm tra số lần thử reset password
    private void validateResetAttempts(String email) {
        // Tạo key để lưu số lần thử reset password bằng cách ghép prefix với email
        String attemptKey = ATTEMPT_PREFIX + email;

        // Lấy số lần thử từ Redis:
        // - Nếu key không tồn tại (null) thì mặc định là 0
        // - Nếu có giá trị thì parse thành số nguyên
        Integer attempts = Optional.ofNullable(
                redisTemplate.opsForValue().get(attemptKey)).map(Integer::parseInt).orElse(0);

        // Kiểm tra nếu số lần thử vượt quá MAX_ATTEMPTS
        // thì throw exception thông báo phải đợi ATTEMPT_TTL phút
        if (attempts >= MAX_ATTEMPTS) {
            throw new TooManyAttemptsException(
                    "Too many reset attempts. Please try again after " + ATTEMPT_TTL + " minutes");
        }
    }

    // Kiểm tra thời gian chờ giữa các lần gửi email
    private void checkEmailCooldown(String email) {
        // Tạo key để kiểm tra cooldown bằng cách ghép prefix với email
        String cooldownKey = EMAIL_COOLDOWN_PREFIX + email;

        // Kiểm tra xem key có tồn tại trong Redis không
        // Nếu tồn tại nghĩa là email đang trong thời gian chờ
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            // Lấy thời gian còn lại (TTL) của key, đơn vị là giây
            Long ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);

            // Nếu TTL > 0 nghĩa là key chưa hết hạn
            // => Email vẫn đang trong thời gian chờ
            if (ttl != null && ttl > 0) {
                // Throw exception thông báo user cần đợi thêm bao nhiêu giây nữa
                throw new RuntimeException("Please wait " + ttl + " seconds before trying again");
            }
        }
    }

    // Set thời gian chờ cho email
    private void setEmailCooldown(String email) {
        // Tạo key cho cooldown bằng cách ghép prefix với email
        String cooldownKey = EMAIL_COOLDOWN_PREFIX + email;

        // Lưu vào Redis với:
        // - Key: EMAIL_COOLDOWN_PREFIX + email
        // - Value: "1" (giá trị không quan trọng, chỉ cần đánh dấu là có cooldown)
        // - Thời gian sống: EMAIL_COOLDOWN phút
        // - Đơn vị thời gian: MINUTES
        // Sau khi hết thời gian, key sẽ tự động bị xóa khỏi Redis
        redisTemplate.opsForValue().set(cooldownKey, "1", EMAIL_COOLDOWN, TimeUnit.MINUTES);
    }

    private String createResetToken(String email) {
        String token = UUID.randomUUID().toString();

        // Tạo các key để lưu trong Redis:
        // - tokenKey: Lưu mapping giữa token và email (TOKEN_PREFIX + token -> email)
        // - emailKey: Lưu mapping giữa email và token (EMAIL_TOKEN_PREFIX + email ->
        // token)
        String tokenKey = TOKEN_PREFIX + token;
        String emailKey = EMAIL_TOKEN_PREFIX + email;

        // Kiểm tra và xóa token cũ của email này nếu tồn tại
        // Điều này đảm bảo mỗi email chỉ có một token hợp lệ tại một thời điểm
        String oldToken = redisTemplate.opsForValue().get(emailKey);
        if (oldToken != null) {
            redisTemplate.delete(TOKEN_PREFIX + oldToken);
        }

        // Lưu token mới vào Redis sử dụng transaction để đảm bảo tính atomic
        // Transaction đảm bảo cả 2 operation (set tokenKey và set emailKey)
        // hoặc được thực hiện hoàn toàn, hoặc không operation nào được thực hiện
        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                // Bắt đầu transaction
                operations.multi();

                // Lưu mapping token -> email với thời gian sống TOKEN_TTL phút
                operations.opsForValue().set(
                        tokenKey,
                        email,
                        TOKEN_TTL,
                        TimeUnit.MINUTES);

                // Lưu mapping email -> token với thời gian sống TOKEN_TTL phút
                operations.opsForValue().set(emailKey, token, TOKEN_TTL, TimeUnit.MINUTES);

                // Thực thi transaction
                return operations.exec();
            }
        });

        return token;
    }

    // Kiểm tra token có hợp lệ không
    public Optional<String> validateToken(String token) {
        // Tạo key cho token bằng cách thêm prefix TOKEN_PREFIX vào token đầu vào
        String key = TOKEN_PREFIX + token;

        // Sử dụng redisTemplate để lấy giá trị (email) từ Redis dựa trên key
        // Bọc kết quả trong Optional để xử lý trường hợp null an toàn
        // Nếu token không tồn tại hoặc hết hạn, sẽ trả về Optional.empty()
        // Nếu token hợp lệ, sẽ trả về Optional chứa email tương ứng
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    // Xóa token sau khi sử dụng
    public void invalidateToken(String token) {
        // Tạo key cho token bằng cách thêm prefix
        String key = TOKEN_PREFIX + token;

        // Lấy email tương ứng với token từ Redis
        String email = redisTemplate.opsForValue().get(key);

        // Nếu tìm thấy email
        if (email != null) {
            // Tạo key cho email bằng cách thêm prefix
            String emailKey = EMAIL_TOKEN_PREFIX + email;
            // Xóa mapping giữa email và token
            redisTemplate.delete(emailKey);
        }

        // Xóa mapping giữa token và email
        redisTemplate.delete(key);
    }
}
