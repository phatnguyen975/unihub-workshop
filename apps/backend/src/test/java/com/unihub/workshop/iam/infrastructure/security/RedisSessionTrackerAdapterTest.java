package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisSessionTrackerAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private RedisSessionTrackerAdapter adapter;

    private final UUID sessionId = UUID.randomUUID();
    private final String jti = "test-jti-123";
    private final Duration ttl = Duration.ofMinutes(15);
    private final String expectedKey = "session:jti:" + sessionId;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
        adapter = new RedisSessionTrackerAdapter(redisTemplate, meterRegistry);
    }

    @Test
    void shouldTrackSessionSuccessfully() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        adapter.trackSession(sessionId, jti, ttl);

        verify(valueOperations).set(expectedKey, jti, ttl);
    }

    @Test
    void shouldGetActiveJtiWhenPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(jti);

        Optional<String> result = adapter.getActiveJti(sessionId);

        assertThat(result).contains(jti);
    }

    @Test
    void shouldReturnEmptyWhenJtiNotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(null);

        Optional<String> result = adapter.getActiveJti(sessionId);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldRemoveSessionSuccessfully() {
        adapter.removeSession(sessionId);

        verify(redisTemplate).delete(expectedKey);
    }

    @Test
    void shouldThrowServiceUnavailableException_whenRedisThrowsOnTrack() {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> adapter.trackSession(sessionId, jti, ttl))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Redis is unavailable");

        verify(meterRegistry).counter("auth.blacklist.redis_unavailable");
        verify(counter).increment();
    }

    @Test
    void shouldThrowServiceUnavailableException_whenRedisThrowsOnGet() {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatThrownBy(() -> adapter.getActiveJti(sessionId))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Redis is unavailable");

        verify(meterRegistry).counter("auth.blacklist.redis_unavailable");
        verify(counter).increment();
    }

    @Test
    void shouldThrowServiceUnavailableException_whenRedisThrowsOnRemove() {
        doThrow(new RedisConnectionFailureException("Redis down")).when(redisTemplate).delete(expectedKey);

        assertThatThrownBy(() -> adapter.removeSession(sessionId))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Redis is unavailable");

        verify(meterRegistry).counter("auth.blacklist.redis_unavailable");
        verify(counter).increment();
    }
}
