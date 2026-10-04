package com.unihub.workshop.workers.search_sync.outbound.cache;

import com.unihub.workshop.workers.search_sync.core.ports.out.SearchCacheInvalidator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSearchCacheInvalidator implements SearchCacheInvalidator {

    private static final String SEARCH_CACHE_PATTERN = "search:*";
    private static final int BATCH_SIZE = 100;

    private final StringRedisTemplate redisTemplate;

    @Override
    public void invalidateAll() {
        try {
            ScanOptions options = ScanOptions.scanOptions()
                .match(SEARCH_CACHE_PATTERN)
                .count(BATCH_SIZE)
                .build();

            List<String> batch = new ArrayList<>();
            long totalDeleted = 0;
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= BATCH_SIZE) {
                        Long deleted = redisTemplate.delete(batch);
                        totalDeleted += deleted != null ? deleted : 0;
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    Long deleted = redisTemplate.delete(batch);
                    totalDeleted += deleted != null ? deleted : 0;
                }
            }

            if (totalDeleted > 0) {
                log.info(
                    "Invalidated {} search cache keys matching pattern '{}' via non-blocking SCAN",
                    totalDeleted,
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
