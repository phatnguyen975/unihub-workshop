package com.unihub.workshop.shared.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.unihub.workshop.shared.exception.AppException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisJtiValidatorTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private RedisJtiValidator validator;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        validator = new RedisJtiValidator(redisTemplate, meterRegistry);
    }

    @Test
    void shouldReturnTrue_whenJtiIsMissingFromBlacklist() {
        when(valueOperations.get("jti:blacklisted:jti-123")).thenReturn(null);
        boolean valid = validator.isTokenValid("jti-123");
        assertTrue(valid);
    }

    @Test
    void shouldReturnFalse_whenJtiIsPresentInBlacklist() {
        when(valueOperations.get("jti:blacklisted:jti-123")).thenReturn(
            "revoked"
        );
        boolean valid = validator.isTokenValid("jti-123");
        assertFalse(valid);
    }

    @Test
    void shouldThrowDomainException_whenRedisIsUnavailable() {
        when(valueOperations.get(anyString())).thenThrow(
            new RedisConnectionFailureException("Connection refused")
        );

        AppException exception = assertThrows(AppException.class, () ->
            validator.isTokenValid("jti-123")
        );
        assertEquals(
            "Failed to check token revocation status. Failing closed.",
            exception.getMessage()
        );
        assertTrue(
            exception.getCause() instanceof RedisConnectionFailureException
        );

        verify(meterRegistry).counter("auth.blacklist.redis_unavailable");
        verify(counter).increment();
    }
}
