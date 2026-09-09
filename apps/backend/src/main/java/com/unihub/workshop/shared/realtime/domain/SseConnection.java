package com.unihub.workshop.shared.realtime.domain;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing an active Server-Sent Events client connection.
 */
public record SseConnection(
        String connectionId,
        UUID userId,
        String role,
        String organizationId,
        SseEmitter emitter,
        Instant connectedAt) {
    public SseConnection {
        if (connectionId == null || connectionId.isBlank()) {
            connectionId = UUID.randomUUID().toString();
        }
        if (connectedAt == null) {
            connectedAt = Instant.now();
        }
    }
}
