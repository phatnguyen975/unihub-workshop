package com.unihub.workshop.workers.search_sync.outbound.cache;

import com.unihub.workshop.workers.search_sync.core.ports.out.SearchCacheInvalidator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSearchCacheInvalidator implements SearchCacheInvalidator {

    private static final String SEARCH_CACHE_PATTERN = "search:*";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void invalidateAll() {
        try {
            Set<String> keys = redisTemplate.keys(SEARCH_CACHE_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                log.info(
                    "Invalidated {} search cache keys matching pattern '{}'",
                    deleted,
                    SEARCH_CACHE_PATTERN
                );
            } else {
                log.debug(
                    "No search cache keys found matching pattern '{}'",
                    SEARCH_CACHE_PATTERN
                );
            }
        } catch (Exception e) {
            log.error(
                "Failed to invalidate search cache keys: {}",
                e.getMessage(),
                e
            );
        }
    }
}
