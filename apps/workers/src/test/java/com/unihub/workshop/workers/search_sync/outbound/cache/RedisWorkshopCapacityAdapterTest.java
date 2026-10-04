package com.unihub.workshop.workers.search_sync.outbound.cache;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class RedisWorkshopCapacityAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisWorkshopCapacityAdapter adapter;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new RedisWorkshopCapacityAdapter(redisTemplate);
    }

    @Test
    @DisplayName(
        "set: unconditionally sets workshop:{id}:capacity to the capacity value"
    )
    void shouldSetCapacityCounterInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        adapter.set(WORKSHOP_ID, 100);

        verify(valueOperations).set(
            "workshop:" + WORKSHOP_ID + ":capacity",
            "100"
        );
    }

    @Test
    @DisplayName("set: null workshopId is handled safely without throwing")
    void shouldHandleNullWorkshopIdSafely() {
        adapter.set(null, 100);
    }
}
