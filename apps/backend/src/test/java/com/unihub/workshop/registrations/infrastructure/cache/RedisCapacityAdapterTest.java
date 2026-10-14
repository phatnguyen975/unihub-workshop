package com.unihub.workshop.registrations.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisCapacityAdapterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisCapacityAdapter adapter;

    private final UUID workshopId = UUID.randomUUID();
    private final String expectedKey = "workshop:" + workshopId + ":capacity";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        adapter = new RedisCapacityAdapter(redisTemplate);
    }

    @Test
    @DisplayName(
        "tryAcquireCapacity: returns true when key exists and remaining capacity >= 0"
    )
    void tryAcquireCapacity_KeyExists_CapacityAvailable_ReturnsTrue() {
        when(redisTemplate.hasKey(expectedKey)).thenReturn(true);
        when(valueOperations.decrement(expectedKey)).thenReturn(5L);

        boolean acquired = adapter.tryAcquireCapacity(workshopId, 50, () -> 0);

        assertThat(acquired).isTrue();
        verify(valueOperations).decrement(expectedKey);
    }

    @Test
    @DisplayName(
        "tryAcquireCapacity: compensates and returns false when remaining capacity < 0"
    )
    void tryAcquireCapacity_CapacityExhausted_CompensatesAndReturnsFalse() {
        when(redisTemplate.hasKey(expectedKey)).thenReturn(true);
        when(valueOperations.decrement(expectedKey)).thenReturn(-1L);

        boolean acquired = adapter.tryAcquireCapacity(workshopId, 50, () -> 0);

        assertThat(acquired).isFalse();
        verify(valueOperations).decrement(expectedKey);
        verify(valueOperations).increment(expectedKey);
    }

    @Test
    @DisplayName(
        "tryAcquireCapacity: lazily initializes key from supplier on cache miss"
    )
    void tryAcquireCapacity_CacheMiss_SelfHealsFromSupplier() {
        when(redisTemplate.hasKey(expectedKey)).thenReturn(false);
        when(valueOperations.setIfAbsent(expectedKey, "10")).thenReturn(true);
        when(valueOperations.decrement(expectedKey)).thenReturn(9L);

        boolean acquired = adapter.tryAcquireCapacity(workshopId, 50, () -> 40);

        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent(expectedKey, "10");
        verify(valueOperations).decrement(expectedKey);
    }

    @Test
    @DisplayName("tryAcquireCapacity: returns false when workshopId is null")
    void tryAcquireCapacity_NullWorkshopId_ReturnsFalse() {
        boolean acquired = adapter.tryAcquireCapacity(null, 50, () -> 0);

        assertThat(acquired).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName(
        "tryAcquireCapacity: throws ServiceUnavailableException with retry-after when Redis is down"
    )
    void tryAcquireCapacity_RedisDown_ThrowsServiceUnavailableException() {
        when(redisTemplate.hasKey(expectedKey)).thenThrow(
            new RedisConnectionFailureException("Redis connection refused")
        );

        assertThatThrownBy(() ->
            adapter.tryAcquireCapacity(workshopId, 50, () -> 0)
        )
            .isInstanceOf(ServiceUnavailableException.class)
            .satisfies(ex -> {
                ServiceUnavailableException sue =
                    (ServiceUnavailableException) ex;
                assertThat(sue.getRetryAfterSeconds()).isEqualTo(30);
            });
    }

    @Test
    @DisplayName("releaseCapacity: increments capacity counter in Redis")
    void releaseCapacity_ValidWorkshopId_IncrementsCounter() {
        adapter.releaseCapacity(workshopId);

        verify(valueOperations).increment(expectedKey);
    }

    @Test
    @DisplayName("releaseCapacity: does nothing when workshopId is null")
    void releaseCapacity_NullWorkshopId_NoOp() {
        adapter.releaseCapacity(null);

        verifyNoInteractions(redisTemplate);
    }
}
