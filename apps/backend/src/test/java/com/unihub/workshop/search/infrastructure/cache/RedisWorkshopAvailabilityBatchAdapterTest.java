package com.unihub.workshop.search.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.workshops.api.WorkshopRegistrationCountPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
class RedisWorkshopAvailabilityBatchAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private WorkshopRegistrationCountPort registrationCountPort;

    private RedisWorkshopAvailabilityBatchAdapter adapter;

    private final UUID workshopId1 = UUID.randomUUID();
    private final UUID workshopId2 = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new RedisWorkshopAvailabilityBatchAdapter(
            redisTemplate,
            registrationCountPort
        );
    }

    private WorkshopCard createCard(UUID id, int capacity) {
        return new WorkshopCard(
            id,
            "slug-" + id,
            "Title " + id,
            null,
            "Location",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "UTC",
            BigDecimal.ZERO,
            "USD",
            capacity,
            null,
            "FREE",
            orgId
        );
    }

    @Test
    @DisplayName(
        "resolveAvailableSeats: returns capacity from Redis when key exists"
    )
    void shouldReturnCapacityFromRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyList())).thenReturn(List.of("42"));

        WorkshopCard card = createCard(workshopId1, 100);
        Map<UUID, Integer> result = adapter.resolveAvailableSeats(
            List.of(card)
        );

        assertThat(result).containsEntry(workshopId1, 42);
    }

    @Test
    @DisplayName(
        "resolveAvailableSeats: falls back to database registration count on Redis cache miss and clamps to 0"
    )
    void shouldFallbackToDatabaseOnCacheMissAndClamp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Key 1 is in Redis (25), Key 2 is missing (null)
        when(valueOperations.multiGet(anyList())).thenReturn(
            Arrays.asList("25", null)
        );
        // Workshop 2 has capacity 10, but 12 registrations (negative clamped to 0)
        when(
            registrationCountPort.countActiveRegistrations(workshopId2)
        ).thenReturn(12L);

        WorkshopCard card1 = createCard(workshopId1, 50);
        WorkshopCard card2 = createCard(workshopId2, 10);

        Map<UUID, Integer> result = adapter.resolveAvailableSeats(
            List.of(card1, card2)
        );

        assertThat(result).containsEntry(workshopId1, 25);
        assertThat(result).containsEntry(workshopId2, 0); // clamped to 0
        verify(registrationCountPort).countActiveRegistrations(workshopId2);
    }

    @Test
    @DisplayName("resolveAvailableSeats: returns empty map on empty list")
    void shouldReturnEmptyMapOnEmptyList() {
        Map<UUID, Integer> result = adapter.resolveAvailableSeats(List.of());
        assertThat(result).isEmpty();
    }
}
