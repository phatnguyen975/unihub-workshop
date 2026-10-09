package com.unihub.workshop.notifications.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
public class InAppNotification {

    private final UUID id;
    private final UUID userId;
    private final String eventKey;
    private final String payload;
    private boolean isRead;
    private Instant readAt;
    private final String actionUrl;
    private final Instant createdAt;
    private Instant updatedAt;
    private final UUID createdBy;
    private final UUID updatedBy;

    @Builder
    public InAppNotification(
        UUID id,
        UUID userId,
        String eventKey,
        String payload,
        boolean isRead,
        Instant readAt,
        String actionUrl,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException(
                "eventKey must not be null or blank"
            );
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.eventKey = eventKey;
        this.payload = payload != null ? payload : "{}";
        this.isRead = isRead;
        this.readAt = readAt;
        this.actionUrl = actionUrl;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    /**
     * Transitions notification from unread to read.
     * Idempotent: returns false if already read without mutating readAt.
     *
     * @param transitionTime server time when read state changed
     * @return true if state changed from false to true, false if already read
     */
    public boolean markAsRead(Instant transitionTime) {
        if (this.isRead) {
            return false;
        }
        this.isRead = true;
        this.readAt = Objects.requireNonNull(
            transitionTime,
            "transitionTime must not be null"
        );
        this.updatedAt = transitionTime;
        return true;
    }
}
