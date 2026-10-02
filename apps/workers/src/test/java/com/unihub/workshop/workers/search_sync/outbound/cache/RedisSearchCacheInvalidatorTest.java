package com.unihub.workshop.workers.search_sync.outbound.cache;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    @DisplayName("invalidateAll: deletes all keys matching pattern search:*")
    void shouldDeleteMatchingKeys() {
        Set<String> matchedKeys = Set.of(
            "search:q:ai:all:sort:0:20",
            "search:q:cloud:all:sort:0:20"
        );
        when(redisTemplate.keys("search:*")).thenReturn(matchedKeys);

        invalidator.invalidateAll();

        verify(redisTemplate).keys("search:*");
        verify(redisTemplate).delete(matchedKeys);
    }

    @Test
    @DisplayName("invalidateAll: no-op if no keys match pattern")
    void shouldNotDeleteWhenNoKeysFound() {
        when(redisTemplate.keys("search:*")).thenReturn(Set.of());

        invalidator.invalidateAll();

        verify(redisTemplate).keys("search:*");
        verifyNoMoreInteractions(redisTemplate);
    }
}
