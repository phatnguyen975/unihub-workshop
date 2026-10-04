package com.unihub.workshop.workers.search_sync.outbound.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisSearchCacheInvalidatorTest {

    private StringRedisTemplate redisTemplate;
    private RedisSearchCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        invalidator = new RedisSearchCacheInvalidator(redisTemplate);
    }

    @Test
    @DisplayName(
        "invalidateAll: deletes all keys matching pattern search:* via SCAN"
    )
    void shouldDeleteMatchingKeys() {
        Cursor<String> cursor = mock();
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
            "search:q:ai:all:sort:0:20",
            "search:q:cloud:all:sort:0:20"
        );
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        invalidator.invalidateAll();

        verify(redisTemplate).scan(any(ScanOptions.class));
        verify(redisTemplate).delete(
            List.of("search:q:ai:all:sort:0:20", "search:q:cloud:all:sort:0:20")
        );
    }

    @Test
    @DisplayName("invalidateAll: no-op if no keys match pattern")
    void shouldNotDeleteWhenNoKeysFound() {
        Cursor<String> cursor = mock();
        when(cursor.hasNext()).thenReturn(false);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        invalidator.invalidateAll();

        verify(redisTemplate).scan(any(ScanOptions.class));
        verifyNoMoreInteractions(redisTemplate);
    }
}
