package com.elevatebanking.service.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisHealthMonitor {
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(fixedRate = 60000)
    public void monitorRedisHealth() {
        try {
            String result = redisTemplate.execute((RedisCallback<String>) connection -> new String(connection.ping()));
            if (!"PONG".equals(result)) {
                log.error("Redis is not responding");
            }
        } catch (Exception e) {
            log.error("Error monitoring Redis health: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupStuckLocks() {
        try {
            Set<String> keys = redisTemplate.keys("transaction_frequency:*");
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl != null && ttl > 300) {
                        log.warn("Found potentially stuck lock: {}", key);
                        redisTemplate.delete(key);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error cleaning up stuck locks: {}", e.getMessage());
        }
    }
}
