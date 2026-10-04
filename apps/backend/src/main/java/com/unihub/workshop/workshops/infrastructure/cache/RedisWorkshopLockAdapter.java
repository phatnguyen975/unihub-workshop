package com.unihub.workshop.workshops.infrastructure.cache;

import com.unihub.workshop.workshops.application.ports.out.WorkshopLockPort;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWorkshopLockAdapter implements WorkshopLockPort {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryLock(String lockKey, Duration ttl) {
        try {
            Boolean success = redisTemplate
                .opsForValue()
                .setIfAbsent(lockKey, "1", ttl);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn(
                "Failed to acquire distributed lock '{}': {}",
                lockKey,
                e.getMessage()
            );
            return false;
        }
    }

    @Override
    public void releaseLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn(
                "Failed to release distributed lock '{}': {}",
                lockKey,
                e.getMessage()
            );
        }
    }
}
