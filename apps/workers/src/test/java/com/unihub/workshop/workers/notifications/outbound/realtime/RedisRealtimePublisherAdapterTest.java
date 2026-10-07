package com.unihub.workshop.workers.notifications.outbound.realtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisRealtimePublisherAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter successCounter;

    @Mock
    private Counter failureCounter;

    private ObjectMapper objectMapper;
    private RedisRealtimePublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new RedisRealtimePublisherAdapter(
            redisTemplate,
            objectMapper,
            Optional.of(meterRegistry)
        );
    }

    @Test
    @DisplayName(
        "Should successfully publish notification.new signal to user channel and increment success metric"
    )
    void shouldPublishSignalSuccessfully() {
        UUID userId = UUID.randomUUID();
        when(
            meterRegistry.counter("notifications.realtime.success")
        ).thenReturn(successCounter);

        adapter.publishNotificationSignal(userId, "notification.new");

        verify(redisTemplate).convertAndSend(
            eq("unihub:stream:user:" + userId),
            contains("notification.new")
        );
        verify(successCounter).increment();
    }

    @Test
    @DisplayName(
        "Should treat Redis exception as soft failure, log warning, and increment failure metric"
    )
    void shouldTreatRedisExceptionAsSoftFailure() {
        UUID userId = UUID.randomUUID();
        when(
            meterRegistry.counter("notifications.realtime.failures")
        ).thenReturn(failureCounter);
        when(
            redisTemplate.convertAndSend(
                eq("unihub:stream:user:" + userId),
                contains("notification.new")
            )
        ).thenThrow(new RuntimeException("Redis connection error"));

        assertDoesNotThrow(() ->
            adapter.publishNotificationSignal(userId, "notification.new")
        );

        verify(failureCounter).increment();
    }

    @Test
    @DisplayName("Should skip publishing when userId is null")
    void shouldSkipWhenUserIdNull() {
        adapter.publishNotificationSignal(null, "notification.new");

        verify(redisTemplate, never()).convertAndSend(any(), any());
    }

    @Test
    @DisplayName(
        "Should publish signal successfully without metric errors when meterRegistry is empty"
    )
    void shouldPublishSignalSuccessfullyWhenMeterRegistryIsEmpty() {
        RedisRealtimePublisherAdapter adapterWithoutMetrics =
            new RedisRealtimePublisherAdapter(
                redisTemplate,
                objectMapper,
                Optional.empty()
            );
        UUID userId = UUID.randomUUID();

        assertDoesNotThrow(() ->
            adapterWithoutMetrics.publishNotificationSignal(
                userId,
                "notification.new"
            )
        );

        verify(redisTemplate).convertAndSend(
            eq("unihub:stream:user:" + userId),
            contains("notification.new")
        );
    }
}
