package com.unihub.workshop.workshops.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workshops.api.WorkshopRegistrationCountPort;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisWorkshopAvailabilityAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private WorkshopRegistrationCountPort registrationCountPort;

    private RedisWorkshopAvailabilityAdapter adapter;
    private final UUID workshopId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new RedisWorkshopAvailabilityAdapter(
            redisTemplate,
            registrationCountPort
        );
    }

    @Test
    @DisplayName(
        "resolveAvailableSeats: reads remaining seats directly from Redis counter without DB query"
    )
    void shouldReturnSeatsFromRedisWhenKeyPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(
            valueOperations.get("workshop:" + workshopId + ":capacity")
        ).thenReturn("42");

        int seats = adapter.resolveAvailableSeats(workshopId, 100);

        assertThat(seats).isEqualTo(42);
        verify(registrationCountPort, never()).countActiveRegistrations(any());
    }

    @Test
    @DisplayName(
        "resolveAvailableSeats: clamps negative Redis counter value to 0"
    )
    void shouldClampNegativeSeatsFromRedisToZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(
            valueOperations.get("workshop:" + workshopId + ":capacity")
        ).thenReturn("-5");

        int seats = adapter.resolveAvailableSeats(workshopId, 100);

        assertThat(seats).isZero();
        verify(registrationCountPort, never()).countActiveRegistrations(any());
    }

    @Test
    @DisplayName(
        "resolveAvailableSeats: falls back to totalCapacity - countActiveRegistrations on Redis miss"
    )
    void shouldFallbackToDbCountWhenRedisMisses() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(
            valueOperations.get("workshop:" + workshopId + ":capacity")
        ).thenReturn(null);
        when(
            registrationCountPort.countActiveRegistrations(workshopId)
        ).thenReturn(25L);

        int seats = adapter.resolveAvailableSeats(workshopId, 100);

        assertThat(seats).isEqualTo(75);
        verify(registrationCountPort).countActiveRegistrations(workshopId);
    }

    @Test
    @DisplayName(
        "resolveAvailableSeats: clamps fallback to 0 when registrations exceed capacity"
    )
    void shouldClampDbFallbackSeatsToZeroWhenOverbooked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(
            valueOperations.get("workshop:" + workshopId + ":capacity")
        ).thenReturn(null);
        when(
            registrationCountPort.countActiveRegistrations(workshopId)
        ).thenReturn(105L);

        int seats = adapter.resolveAvailableSeats(workshopId, 100);

        assertThat(seats).isZero();
    }

    @Test
    @DisplayName(
        "resolveAvailableSeats: returns totalCapacity when DB count query fails during fallback"
    )
    void shouldFallbackToCapacityWhenDbQueryFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(
            valueOperations.get("workshop:" + workshopId + ":capacity")
        ).thenReturn(null);
        when(
            registrationCountPort.countActiveRegistrations(workshopId)
        ).thenThrow(new RuntimeException("DB unavailable"));

        int seats = adapter.resolveAvailableSeats(workshopId, 100);

        assertThat(seats).isEqualTo(100);
    }
}
