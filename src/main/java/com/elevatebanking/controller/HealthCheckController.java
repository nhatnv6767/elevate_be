package com.elevatebanking.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthCheckController {
    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping("/redis")
    public ResponseEntity<?> checkRedisHealth() {
        try {
            Boolean result = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                try {
                    connection.ping();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });

            if (Boolean.TRUE.equals(result)) {
                return ResponseEntity.ok(Map.of(
                        "status", "UP",
                        "message", "Redis is working properly"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "status", "DOWN",
                                "message", "Redis is not responding"
                        ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "ERROR",
                            "message", e.getMessage()
                    ));
        }
    }
}
