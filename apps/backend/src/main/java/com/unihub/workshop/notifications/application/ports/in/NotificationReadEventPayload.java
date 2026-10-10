package com.unihub.workshop.notifications.application.ports.in;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationReadEventPayload(
    UUID id,
    boolean all,
    long unreadCount,
    Instant readAt
) {
    public static NotificationReadEventPayload forSingle(
        UUID id,
        long unreadCount,
        Instant readAt
    ) {
        return new NotificationReadEventPayload(id, false, unreadCount, readAt);
    }

    public static NotificationReadEventPayload forAll(Instant readAt) {
        return new NotificationReadEventPayload(null, true, 0L, readAt);
    }
}
