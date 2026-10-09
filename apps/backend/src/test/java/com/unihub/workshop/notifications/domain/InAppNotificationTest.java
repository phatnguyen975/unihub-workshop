package com.unihub.workshop.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.notifications.domain.model.InAppNotification;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InAppNotificationTest {

    @Test
    @DisplayName("should create InAppNotification with valid required fields")
    void shouldCreateInAppNotificationWithValidFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        InAppNotification notification = InAppNotification.builder()
            .id(id)
            .userId(userId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload("{\"workshopId\":\"123\"}")
            .isRead(false)
            .actionUrl("https://unihub.edu.vn/workshops/123")
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThat(notification.getId()).isEqualTo(id);
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getEventKey()).isEqualTo(
            "WORKSHOP_RESCHEDULED"
        );
        assertThat(notification.getPayload()).isEqualTo(
            "{\"workshopId\":\"123\"}"
        );
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getReadAt()).isNull();
        assertThat(notification.getActionUrl()).isEqualTo(
            "https://unihub.edu.vn/workshops/123"
        );
        assertThat(notification.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("should reject creation when required fields are missing")
    void shouldRejectWhenRequiredFieldsMissing() {
        assertThatThrownBy(() ->
            InAppNotification.builder().userId(null).eventKey("EVENT").build()
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
            InAppNotification.builder()
                .userId(UUID.randomUUID())
                .eventKey("")
                .build()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName(
        "should transition from unread to read when markAsRead is called"
    )
    void shouldTransitionToRead() {
        Instant now = Instant.now();
        Instant readTime = now.plusSeconds(60);

        InAppNotification notification = InAppNotification.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload("{}")
            .isRead(false)
            .createdAt(now)
            .updatedAt(now)
            .build();

        boolean changed = notification.markAsRead(readTime);

        assertThat(changed).isTrue();
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(readTime);
    }

    @Test
    @DisplayName(
        "should be idempotent when already read and preserve existing readAt"
    )
    void shouldBeIdempotentWhenAlreadyRead() {
        Instant now = Instant.now();
        Instant initialReadTime = now.plusSeconds(30);
        Instant secondReadTime = now.plusSeconds(60);

        InAppNotification notification = InAppNotification.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload("{}")
            .isRead(true)
            .readAt(initialReadTime)
            .createdAt(now)
            .updatedAt(now)
            .build();

        boolean changed = notification.markAsRead(secondReadTime);

        assertThat(changed).isFalse();
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(initialReadTime);
    }
}
