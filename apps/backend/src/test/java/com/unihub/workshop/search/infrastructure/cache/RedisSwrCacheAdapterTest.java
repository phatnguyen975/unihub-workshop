package com.unihub.workshop.search.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.search.domain.model.SwrCacheEntry;
import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisSwrCacheAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(
        new JavaTimeModule()
    );
    private RedisSwrCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisSwrCacheAdapter(redisTemplate);
    }

    @Test
    @DisplayName("get: returns empty when key not present in Redis")
    void shouldReturnEmptyWhenKeyMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("search:q:test")).thenReturn(null);

        Optional<SwrCacheEntry> result = adapter.get("search:q:test");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(
        "get: parses and returns SwrCacheEntry when valid JSON exists in Redis"
    )
    void shouldReturnParsedEntryWhenKeyPresent() throws Exception {
        CursorPage<WorkshopCard> page = new CursorPage<>(
            List.of(),
            "cursor123",
            true
        );
        SwrCacheEntry expected = new SwrCacheEntry(Instant.now(), page);
        String json = objectMapper.writeValueAsString(expected);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("search:q:test")).thenReturn(json);

        Optional<SwrCacheEntry> result = adapter.get("search:q:test");
        assertThat(result).isPresent();
        assertThat(result.get().result().nextCursor()).isEqualTo("cursor123");
        assertThat(result.get().result().hasMore()).isTrue();
    }

    @Test
    @DisplayName("get: returns empty when JSON deserialization fails")
    void shouldReturnEmptyWhenJsonCorrupted() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("search:q:test")).thenReturn("invalid-json{");

        Optional<SwrCacheEntry> result = adapter.get("search:q:test");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("put: writes JSON with configured hard TTL")
    void shouldWriteJsonWithHardTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CursorPage<WorkshopCard> page = CursorPage.empty();
        SwrCacheEntry entry = new SwrCacheEntry(Instant.now(), page);

        adapter.put("search:q:test", entry, Duration.ofHours(24));

        verify(valueOperations).set(
            eq("search:q:test"),
            any(String.class),
            eq(Duration.ofHours(24))
        );
    }

    @Test
    @DisplayName("tryLock: returns true when Redis setIfAbsent succeeds")
    void shouldReturnTrueWhenLockAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(
            valueOperations.setIfAbsent(
                "lock:search:q:test",
                "1",
                Duration.ofSeconds(10)
            )
        ).thenReturn(Boolean.TRUE);

        boolean locked = adapter.tryLock(
            "lock:search:q:test",
            Duration.ofSeconds(10)
        );
        assertThat(locked).isTrue();
    }

    @Test
    @DisplayName("tryLock: returns false when Redis setIfAbsent fails")
    void shouldReturnFalseWhenLockNotAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(
            valueOperations.setIfAbsent(
                "lock:search:q:test",
                "1",
                Duration.ofSeconds(10)
            )
        ).thenReturn(Boolean.FALSE);

        boolean locked = adapter.tryLock(
            "lock:search:q:test",
            Duration.ofSeconds(10)
        );
        assertThat(locked).isFalse();
    }

    @Test
    @DisplayName("releaseLock: deletes lock key from Redis")
    void shouldDeleteLockKeyOnRelease() {
        adapter.releaseLock("lock:search:q:test");
        verify(redisTemplate).delete("lock:search:q:test");
    }
}
