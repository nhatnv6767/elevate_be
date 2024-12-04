package com.elevatebanking.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TestController {

    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/message")
    public ResponseEntity<?> testMessage(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String key = "test:message:" + LocalDateTime.now();

        redisTemplate.opsForValue().set(key, message, 5, TimeUnit.MINUTES);

        kafkaTemplate.send("elevate.test", key, Map.of(
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        ));

        return ResponseEntity.ok(Map.of(
                "key", key,
                "message", message,
                "status", "Message saved to Redis and published to Kafka"
        ));
    }

    @GetMapping("/message/{key}")
    public ResponseEntity<?> getMessage(@PathVariable String key) {
        String message = redisTemplate.opsForValue().get(key);
        if (message == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "key", key,
                "message", message
        ));
    }

    @GetMapping("/redis/info")
    public ResponseEntity<?> getRedisInfo() {
        Boolean isConnected = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping() != null;

        return ResponseEntity.ok(Map.of(
                "connected", isConnected,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

}
