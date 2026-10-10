package com.unihub.workshop.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.notifications.application.ports.in.GetNotificationsQuery;
import com.unihub.workshop.notifications.application.ports.in.NotificationsResult;
import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import com.unihub.workshop.notifications.application.usecases.GetNotificationsService;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetNotificationsServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private GetNotificationsService service;

    @Test
    @DisplayName(
        "should return notifications result with items, cursor, and unread count"
    )
    void shouldReturnNotificationsResult_whenValidQueryProvided() {
        UUID userId = UUID.randomUUID();
        GetNotificationsQuery query = GetNotificationsQuery.of(
            userId,
            false,
            null,
            20
        );

        InAppNotification notification = InAppNotification.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload("{\"key\":\"value\"}")
            .isRead(false)
            .createdAt(Instant.parse("2026-10-08T10:00:00Z"))
            .build();

        CursorPage<InAppNotification> cursorPage = new CursorPage<>(
            List.of(notification),
            "next-cursor-token",
            true
        );

        when(
            notificationRepository.findNotificationsByUserId(
                userId,
                false,
                null,
                20
            )
        ).thenReturn(cursorPage);
        when(notificationRepository.countUnreadByUserId(userId)).thenReturn(3L);

        NotificationsResult result = service.getNotifications(query);

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0)).isEqualTo(notification);
        assertThat(result.unreadCount()).isEqualTo(3L);
        assertThat(result.nextCursor()).isEqualTo("next-cursor-token");
        assertThat(result.hasMore()).isTrue();

        verify(notificationRepository).findNotificationsByUserId(
            userId,
            false,
            null,
            20
        );
        verify(notificationRepository).countUnreadByUserId(userId);
    }

    @Test
    @DisplayName(
        "should query notifications without isRead filter when isRead is null"
    )
    void shouldQueryNotifications_whenIsReadIsNull() {
        UUID userId = UUID.randomUUID();
        GetNotificationsQuery query = GetNotificationsQuery.of(
            userId,
            null,
            "cur-1",
            10
        );

        CursorPage<InAppNotification> cursorPage = new CursorPage<>(
            List.of(),
            null,
            false
        );

        when(
            notificationRepository.findNotificationsByUserId(
                userId,
                null,
                "cur-1",
                10
            )
        ).thenReturn(cursorPage);
        when(notificationRepository.countUnreadByUserId(userId)).thenReturn(0L);

        NotificationsResult result = service.getNotifications(query);

        assertThat(result.items()).isEmpty();
        assertThat(result.unreadCount()).isEqualTo(0L);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasMore()).isFalse();

        verify(notificationRepository).findNotificationsByUserId(
            userId,
            null,
            "cur-1",
            10
        );
        verify(notificationRepository).countUnreadByUserId(userId);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when query is null")
    void shouldThrowIllegalArgumentException_whenQueryIsNull() {
        assertThatThrownBy(() -> service.getNotifications(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("query must not be null");
    }
}
