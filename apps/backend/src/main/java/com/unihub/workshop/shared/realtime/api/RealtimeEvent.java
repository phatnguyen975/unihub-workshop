package com.unihub.workshop.shared.realtime.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Public event contract representing a real-time event payload in the Unified Event Catalog.
 *
 * @param eventId   unique identifier for client-side deduplication
 * @param eventType the catalog event name (e.g. notification.new, attendance.updated)
 * @param timestamp event creation timestamp in ISO 8601 UTC format
 * @param payload   event-specific payload data
 * @param <T>       the payload type
 */
public record RealtimeEvent<T>(
    @JsonProperty("eventId") UUID eventId,
    @JsonProperty("eventType") String eventType,
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    Instant timestamp,
    @JsonProperty("payload") T payload
) {
    public RealtimeEvent {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static <T> RealtimeEvent<T> of(
        RealtimeEventType eventType,
        T payload
    ) {
        return new RealtimeEvent<>(
            UUID.randomUUID(),
            eventType != null ? eventType.getValue() : null,
            Instant.now(),
            payload
        );
    }

    public static <T> RealtimeEvent<T> of(String eventType, T payload) {
        return new RealtimeEvent<>(
            UUID.randomUUID(),
            eventType,
            Instant.now(),
            payload
        );
    }
}
