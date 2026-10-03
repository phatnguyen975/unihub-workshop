package com.unihub.workshop.search.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.search.application.ports.out.SearchCachePort;
import com.unihub.workshop.search.domain.model.SwrCacheEntry;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSwrCacheAdapter implements SearchCachePort {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(
        new JavaTimeModule()
    );

    @Override
    public Optional<SwrCacheEntry> get(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            SwrCacheEntry entry = objectMapper.readValue(
                json,
                SwrCacheEntry.class
            );
            return Optional.ofNullable(entry);
        } catch (Exception e) {
            log.warn(
                "Failed to read SWR cache key '{}': {}",
                key,
                e.getMessage()
            );
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, SwrCacheEntry entry, Duration hardTtl) {
        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(key, json, hardTtl);
            log.debug(
                "Stored SWR cache key '{}' with hard TTL {}",
                key,
                hardTtl
            );
        } catch (Exception e) {
            log.warn(
                "Failed to write SWR cache key '{}': {}",
                key,
                e.getMessage()
            );
        }
    }

    @Override
    public boolean tryLock(String lockKey, Duration lockTimeout) {
        try {
            Boolean success = redisTemplate
                .opsForValue()
                .setIfAbsent(lockKey, "1", lockTimeout);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn(
                "Failed to acquire non-blocking SWR lock '{}': {}",
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
                "Failed to release SWR lock '{}': {}",
                lockKey,
                e.getMessage()
            );
        }
    }
}
