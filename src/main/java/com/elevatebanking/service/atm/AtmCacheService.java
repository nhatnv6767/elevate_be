package com.elevatebanking.service.atm;

import com.elevatebanking.entity.atm.AtmMachine;
import io.jsonwebtoken.io.SerializationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AtmCacheService {
    private final RedisTemplate<String, AtmMachine> redisTemplate;
    private final AtmManagementService atmManagementService;

    public AtmMachine getAtmWithCache(String atmId) {
        try {
            String key = "atm:" + atmId;
            AtmMachine atm = redisTemplate.opsForValue().get(key);
            if (atm == null) {
                atm = atmManagementService.getAtmById(atmId);
                redisTemplate.opsForValue().set(key, atm, 30, TimeUnit.MINUTES);
            }
            return atm;
        } catch (SerializationException e) {
            log.error("Error deserializing ATM data: {}", e.getMessage());
            // Xóa cache entry lỗi
            redisTemplate.delete("atm:" + atmId);
            throw new RuntimeException("Error reading ATM data from cache", e);
        }
    }
}
