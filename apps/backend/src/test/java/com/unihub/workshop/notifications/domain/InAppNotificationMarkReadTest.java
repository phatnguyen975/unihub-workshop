package com.unihub.workshop.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.notifications.domain.model.InAppNotification;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InAppNotificationMarkReadTest {

    @Test
    @DisplayName(
        "markAsRead should transition unread notification to read with readAt and updatedAt"
    )
    void markAsRead_whenUnread_transitionsToRead() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-08T10:00:00Z");
        Instant transitionTime = Instant.parse("2026-10-08T15:30:00Z");

        InAppNotification notification = InAppNotification.builder()
            .id(id)
            .userId(userId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload("{\"workshopId\":\"w-1\"}")
            .isRead(false)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();

        boolean transitioned = notification.markAsRead(transitionTime);

        assertThat(transitioned).isTrue();
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(transitionTime);
        assertThat(notification.getUpdatedAt()).isEqualTo(transitionTime);
    }

    @Test
    @DisplayName(
        "markAsRead should be idempotent and return false when already read"
    )
    void markAsRead_whenAlreadyRead_returnsFalseAndPreservesExistingReadAt() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-08T10:00:00Z");
        Instant initialReadAt = Instant.parse("2026-10-08T11:00:00Z");
        Instant secondCallTime = Instant.parse("2026-10-08T15:30:00Z");

        InAppNotification notification = InAppNotification.builder()
            .id(id)
            .userId(userId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload("{\"workshopId\":\"w-1\"}")
            .isRead(true)
            .readAt(initialReadAt)
            .createdAt(createdAt)
            .updatedAt(initialReadAt)
            .build();

        boolean transitioned = notification.markAsRead(secondCallTime);

        assertThat(transitioned).isFalse();
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(initialReadAt);
        assertThat(notification.getUpdatedAt()).isEqualTo(initialReadAt);
    }

    @Test
    @DisplayName(
        "markAsRead should throw NullPointerException when transitionTime is null"
    )
    void markAsRead_whenTransitionTimeNull_throwsException() {
        InAppNotification notification = InAppNotification.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .eventKey("WORKSHOP_RESCHEDULED")
            .isRead(false)
            .build();

        assertThatThrownBy(() -> notification.markAsRead(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("transitionTime must not be null");
    }
}
