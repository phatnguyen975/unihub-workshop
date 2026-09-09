package com.unihub.workshop.shared.realtime.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.realtime.contracts.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.domain.RealtimeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Redis Pub/Sub adapter broadcasting typed realtime events across distributed
 * instances.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRealtimePublisherAdapter implements RealtimeEventPublisher {

    public static final String USER_CHANNEL_PREFIX = "unihub:stream:user:";
    public static final String WORKSHOP_CHANNEL_PREFIX = "unihub:stream:workshop:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void publishToUser(UUID userId, RealtimeEvent<?> event) {
        if (userId == null || event == null) {
            log.warn("Cannot publish realtime event: userId or event is null");
            return;
        }
        if (redisTemplate == null) {
            log.warn("StringRedisTemplate is not available; cannot publish realtime event {} to user {}",
                    event.eventType(), userId);
            return;
        }
        String channel = USER_CHANNEL_PREFIX + userId;
        publish(channel, event);
    }

    @Override
    public void publishToWorkshop(UUID workshopId, RealtimeEvent<?> event) {
        if (workshopId == null || event == null) {
            log.warn("Cannot publish realtime event: workshopId or event is null");
            return;
        }
        if (redisTemplate == null) {
            log.warn("StringRedisTemplate is not available; cannot publish realtime event {} to workshop {}",
                    event.eventType(), workshopId);
            return;
        }
        String channel = WORKSHOP_CHANNEL_PREFIX + workshopId;
        publish(channel, event);
    }

    private void publish(String channel, RealtimeEvent<?> event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(channel, jsonPayload);
            log.debug("Published event {} to Redis channel {}", event.eventType(), channel);
        } catch (Exception ex) {
            log.error("Failed to publish event {} to Redis channel {}: {}", event.eventType(), channel,
                    ex.getMessage());
        }
    }
}
