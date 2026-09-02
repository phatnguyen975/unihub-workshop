package com.unihub.workshop.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisJtiValidatorTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisJtiValidator validator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        validator = new RedisJtiValidator(redisTemplate);
    }

    @Test
    void shouldReturnTrue_whenJtiIsMissingFromBlacklist() {
        when(valueOperations.get("jti:blacklisted:jti-123")).thenReturn(null);
        boolean valid = validator.isTokenValid("jti-123");
        assertTrue(valid);
    }

    @Test
    void shouldReturnFalse_whenJtiIsPresentInBlacklist() {
        when(valueOperations.get("jti:blacklisted:jti-123")).thenReturn("revoked");
        boolean valid = validator.isTokenValid("jti-123");
        assertFalse(valid);
    }

    @Test
    void shouldThrowDomainException_whenRedisIsUnavailable() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("Connection refused"));

        com.unihub.workshop.shared.exception.AppException exception = assertThrows(com.unihub.workshop.shared.exception.AppException.class,
                () -> validator.isTokenValid("jti-123"));
        assertEquals("Failed to check token revocation status. Failing closed.", exception.getMessage());
        assertTrue(exception.getCause() instanceof RedisConnectionFailureException);
    }
}
