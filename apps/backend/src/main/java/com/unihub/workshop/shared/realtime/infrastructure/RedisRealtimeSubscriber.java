package com.unihub.workshop.shared.realtime.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.realtime.application.ports.out.WorkshopAuthorizationPort;
import com.unihub.workshop.shared.realtime.domain.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Distributed Redis Pub/Sub subscriber listening to user and workshop channels.
 * Deserializes published event envelopes and delivers them to local active SSE
 * emitters.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRealtimeSubscriber implements MessageListener {

    private final SseEmitterRegistry registry;
    private final WorkshopAuthorizationPort workshopAuthorizationPort;
    private final RealtimeMetrics realtimeMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getChannel() == null || message.getBody() == null) {
            return;
        }

        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String jsonBody = new String(message.getBody(), StandardCharsets.UTF_8);

            log.debug("Received Redis Pub/Sub message on channel: {}", channel);

            JsonNode rootNode = objectMapper.readTree(jsonBody);
            String eventId = rootNode.has("eventId") ? rootNode.get("eventId").asText() : UUID.randomUUID().toString();
            String eventType = rootNode.has("eventType") ? rootNode.get("eventType").asText() : "message";

            if (channel.startsWith(RedisRealtimePublisherAdapter.USER_CHANNEL_PREFIX)) {
                String userIdStr = channel.substring(RedisRealtimePublisherAdapter.USER_CHANNEL_PREFIX.length());
                UUID userId = UUID.fromString(userIdStr);
                deliverToUser(userId, eventId, eventType, jsonBody);
            } else if (channel.startsWith(RedisRealtimePublisherAdapter.WORKSHOP_CHANNEL_PREFIX)) {
                String workshopIdStr = channel
                        .substring(RedisRealtimePublisherAdapter.WORKSHOP_CHANNEL_PREFIX.length());
                UUID workshopId = UUID.fromString(workshopIdStr);
                deliverToWorkshop(workshopId, eventId, eventType, jsonBody);
            } else {
                log.warn("Unrecognized Redis Pub/Sub channel format: {}", channel);
            }
        } catch (Exception ex) {
            log.error("Failed to process Redis Pub/Sub message: {}", ex.getMessage(), ex);
        }
    }

    private void deliverToUser(UUID userId, String eventId, String eventType, String jsonBody) {
        Set<SseEmitter> emitters = registry.getEmitters(userId);
        if (emitters.isEmpty()) {
            log.trace("No active connections on this node for user {}, discarding message safely", userId);
            return;
        }

        for (SseEmitter emitter : emitters) {
            sendEvent(userId, emitter, eventId, eventType, jsonBody);
        }
    }

    private void deliverToWorkshop(UUID workshopId, String eventId, String eventType, String jsonBody) {
        Set<UUID> connectedUserIds = registry.getConnectedUserIds();
        if (connectedUserIds.isEmpty()) {
            log.trace("No active connections on this node, discarding workshop message safely for {}", workshopId);
            return;
        }

        if (workshopAuthorizationPort == null) {
            log.warn("WorkshopAuthorizationPort is not configured, failing closed: no workshop events delivered for {}",
                    workshopId);
            return;
        }

        Set<UUID> authorizedUserIds = workshopAuthorizationPort.filterAuthorizedUserIds(connectedUserIds, workshopId);
        if (authorizedUserIds == null || authorizedUserIds.isEmpty()) {
            log.trace("No authorized users connected on this node for workshop {}", workshopId);
            return;
        }

        for (UUID userId : authorizedUserIds) {
            Set<SseEmitter> emitters = registry.getEmitters(userId);
            for (SseEmitter emitter : emitters) {
                sendEvent(userId, emitter, eventId, eventType, jsonBody);
            }
        }
    }

    private void sendEvent(UUID userId, SseEmitter emitter, String eventId, String eventType, String jsonBody) {
        try {
            SseEmitter.SseEventBuilder eventBuilder = SseEmitter.event()
                    .id(eventId)
                    .name(eventType)
                    .data(jsonBody);
            emitter.send(eventBuilder);
            if (realtimeMetrics != null) {
                realtimeMetrics.incrementEventsPushed(eventType);
            }
            log.debug("Successfully delivered SSE event {} (id: {}) to user {}", eventType, eventId, userId);
        } catch (Exception ex) {
            log.debug("Failed to deliver SSE event to user {}, evicting broken emitter: {}", userId, ex.getMessage());
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            registry.remove(userId, emitter);
            if (realtimeMetrics != null) {
                realtimeMetrics.incrementDisconnects("error");
            }
        }
    }
}
