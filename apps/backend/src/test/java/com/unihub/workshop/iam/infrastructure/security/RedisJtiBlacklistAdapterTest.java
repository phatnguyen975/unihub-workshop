package com.unihub.workshop.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisJtiBlacklistAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private RedisJtiBlacklistAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
        adapter = new RedisJtiBlacklistAdapter(redisTemplate, meterRegistry);
    }

    @Test
    void shouldBlacklistJtiSuccessfully() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        adapter.blacklistJti("test-jti", Duration.ofMinutes(15));

        verify(valueOperations).set(
            "jti:blacklisted:test-jti",
            "revoked",
            Duration.ofMinutes(15)
        );
    }

    @Test
    void shouldThrowServiceUnavailableExceptionAndIncrementMetric_whenRedisFails() {
        when(redisTemplate.opsForValue()).thenThrow(
            new RedisConnectionFailureException("Redis down")
        );

        assertThatThrownBy(() ->
            adapter.blacklistJti("test-jti", Duration.ofMinutes(15))
        )
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("Redis is unavailable");

        verify(meterRegistry).counter("auth.blacklist.redis_unavailable");
        verify(counter).increment();
    }
}
