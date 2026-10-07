package com.unihub.workshop.workers.notifications.outbound.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.workers.notifications.core.ports.out.RealtimeEventPublisherPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisRealtimePublisherAdapter
    implements RealtimeEventPublisherPort
{

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public RedisRealtimePublisherAdapter(
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        Optional<MeterRegistry> meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry =
            meterRegistry != null ? meterRegistry.orElse(null) : null;
    }

    @Override
    public void publishNotificationSignal(UUID userId, String eventType) {
        if (userId == null) {
            log.warn("Skipping real-time notification signal: userId is null");
            return;
        }

        String channel = "unihub:stream:user:" + userId;
        String type =
            eventType != null && !eventType.isBlank()
                ? eventType
                : "notification.new";

        try {
            Map<String, Object> messagePayload = Map.of(
                "type",
                type,
                "userId",
                userId.toString()
            );
            String jsonMessage = objectMapper.writeValueAsString(
                messagePayload
            );
            redisTemplate.convertAndSend(channel, jsonMessage);

            if (meterRegistry != null) {
                meterRegistry
                    .counter("notifications.realtime.success")
                    .increment();
            }
            log.debug(
                "Published real-time notification signal to channel '{}'",
                channel
            );
        } catch (Exception e) {
            if (meterRegistry != null) {
                meterRegistry
                    .counter("notifications.realtime.failures")
                    .increment();
            }
            log.warn(
                "Soft failure: failed to publish real-time notification signal to channel '{}': {}",
                channel,
                e.getMessage()
            );
        }
    }
}
