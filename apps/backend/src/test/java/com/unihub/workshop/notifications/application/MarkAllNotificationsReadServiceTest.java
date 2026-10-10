package com.unihub.workshop.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.notifications.application.ports.in.MarkAllReadResult;
import com.unihub.workshop.notifications.application.ports.in.NotificationReadEventPayload;
import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import com.unihub.workshop.notifications.application.usecases.MarkAllNotificationsReadService;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkAllNotificationsReadServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Captor
    private ArgumentCaptor<
        RealtimeEvent<NotificationReadEventPayload>
    > eventCaptor;

    private MarkAllNotificationsReadService service;

    @BeforeEach
    void setUp() {
        service = new MarkAllNotificationsReadService(
            notificationRepository,
            realtimeEventPublisher
        );
    }

    @Test
    @DisplayName(
        "should update all unread notifications to read, return updatedCount and unreadCount 0, and publish SSE event"
    )
    void markAllAsRead_whenUnreadExist_success() {
        UUID userId = UUID.randomUUID();

        when(
            notificationRepository.markAllAsReadByUserId(
                eq(userId),
                any(Instant.class)
            )
        ).thenReturn(5);

        MarkAllReadResult result = service.markAllAsRead(userId);

        assertThat(result.updatedCount()).isEqualTo(5);
        assertThat(result.unreadCount()).isEqualTo(0L);

        verify(notificationRepository).markAllAsReadByUserId(
            eq(userId),
            any(Instant.class)
        );

        verify(realtimeEventPublisher).publishToUser(
            eq(userId),
            eventCaptor.capture()
        );

        RealtimeEvent<NotificationReadEventPayload> capturedEvent =
            eventCaptor.getValue();
        assertThat(capturedEvent.eventType()).isEqualTo(
            RealtimeEventType.NOTIFICATION_READ.getValue()
        );
        assertThat(capturedEvent.payload().id()).isNull();
        assertThat(capturedEvent.payload().all()).isTrue();
        assertThat(capturedEvent.payload().unreadCount()).isEqualTo(0L);
        assertThat(capturedEvent.payload().readAt()).isNotNull();
    }

    @Test
    @DisplayName(
        "should return updatedCount 0 and unreadCount 0, and suppress SSE event when no unread notifications exist"
    )
    void markAllAsRead_whenNoUnread_idempotentNoSse() {
        UUID userId = UUID.randomUUID();

        when(
            notificationRepository.markAllAsReadByUserId(
                eq(userId),
                any(Instant.class)
            )
        ).thenReturn(0);

        MarkAllReadResult result = service.markAllAsRead(userId);

        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.unreadCount()).isEqualTo(0L);

        verify(notificationRepository).markAllAsReadByUserId(
            eq(userId),
            any(Instant.class)
        );
        verify(realtimeEventPublisher, never()).publishToUser(any(), any());
    }

    @Test
    @DisplayName(
        "should not fail transaction if SSE publishing throws an exception"
    )
    void markAllAsRead_whenSsePublishThrowsException_logsWarningAndCompletesSuccessfully() {
        UUID userId = UUID.randomUUID();

        when(
            notificationRepository.markAllAsReadByUserId(
                eq(userId),
                any(Instant.class)
            )
        ).thenReturn(3);

        doThrow(new RuntimeException("Redis connection error"))
            .when(realtimeEventPublisher)
            .publishToUser(eq(userId), any());

        MarkAllReadResult result = service.markAllAsRead(userId);

        assertThat(result.updatedCount()).isEqualTo(3);
        assertThat(result.unreadCount()).isEqualTo(0L);
        verify(notificationRepository).markAllAsReadByUserId(
            eq(userId),
            any(Instant.class)
        );
    }
}
