package com.unihub.workshop.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.notifications.application.ports.in.MarkNotificationReadResult;
import com.unihub.workshop.notifications.application.ports.in.NotificationReadEventPayload;
import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import com.unihub.workshop.notifications.application.usecases.MarkNotificationReadService;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import java.time.Instant;
import java.util.Optional;
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
class MarkNotificationReadServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Captor
    private ArgumentCaptor<
        RealtimeEvent<NotificationReadEventPayload>
    > eventCaptor;

    private MarkNotificationReadService service;

    @BeforeEach
    void setUp() {
        service = new MarkNotificationReadService(
            notificationRepository,
            realtimeEventPublisher
        );
    }

    @Test
    @DisplayName(
        "should transition unread notification to read, save to repository, compute unreadCount, and publish SSE event"
    )
    void markAsRead_whenUnread_success() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-08T10:00:00Z");

        InAppNotification notification = InAppNotification.builder()
            .id(notificationId)
            .userId(userId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .payload("{\"workshopTitle\":\"Career Sprint 101\"}")
            .isRead(false)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();

        when(notificationRepository.findById(notificationId)).thenReturn(
            Optional.of(notification)
        );
        when(
            notificationRepository.save(any(InAppNotification.class))
        ).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.countUnreadByUserId(userId)).thenReturn(5L);

        MarkNotificationReadResult result = service.markAsRead(
            notificationId,
            userId
        );

        assertThat(result.notification().isRead()).isTrue();
        assertThat(result.notification().getReadAt()).isNotNull();
        assertThat(result.unreadCount()).isEqualTo(5L);

        verify(notificationRepository).save(notification);
        verify(notificationRepository).countUnreadByUserId(userId);

        verify(realtimeEventPublisher).publishToUser(
            eq(userId),
            eventCaptor.capture()
        );

        RealtimeEvent<NotificationReadEventPayload> capturedEvent =
            eventCaptor.getValue();
        assertThat(capturedEvent.eventType()).isEqualTo(
            RealtimeEventType.NOTIFICATION_READ.getValue()
        );
        assertThat(capturedEvent.payload().id()).isEqualTo(notificationId);
        assertThat(capturedEvent.payload().all()).isFalse();
        assertThat(capturedEvent.payload().unreadCount()).isEqualTo(5L);
        assertThat(capturedEvent.payload().readAt()).isNotNull();
    }

    @Test
    @DisplayName(
        "should be idempotent when already read: return current state without saving and suppress SSE event"
    )
    void markAsRead_whenAlreadyRead_idempotentNoSaveNoSse() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-08T10:00:00Z");
        Instant initialReadAt = Instant.parse("2026-10-08T11:00:00Z");

        InAppNotification notification = InAppNotification.builder()
            .id(notificationId)
            .userId(userId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .isRead(true)
            .readAt(initialReadAt)
            .createdAt(createdAt)
            .updatedAt(initialReadAt)
            .build();

        when(notificationRepository.findById(notificationId)).thenReturn(
            Optional.of(notification)
        );
        when(notificationRepository.countUnreadByUserId(userId)).thenReturn(3L);

        MarkNotificationReadResult result = service.markAsRead(
            notificationId,
            userId
        );

        assertThat(result.notification().isRead()).isTrue();
        assertThat(result.notification().getReadAt()).isEqualTo(initialReadAt);
        assertThat(result.unreadCount()).isEqualTo(3L);

        verify(notificationRepository, never()).save(any());
        verify(realtimeEventPublisher, never()).publishToUser(any(), any());
    }

    @Test
    @DisplayName(
        "should throw ForbiddenException and perform no DB write or SSE publish when notification belongs to another user"
    )
    void markAsRead_whenBelongsToAnotherUser_throwsForbiddenException() {
        UUID notificationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        InAppNotification notification = InAppNotification.builder()
            .id(notificationId)
            .userId(ownerId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .isRead(false)
            .build();

        when(notificationRepository.findById(notificationId)).thenReturn(
            Optional.of(notification)
        );

        assertThatThrownBy(() -> service.markAsRead(notificationId, callerId))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining(
                "You do not have permission to access this notification."
            );

        verify(notificationRepository, never()).save(any());
        verify(realtimeEventPublisher, never()).publishToUser(any(), any());
    }

    @Test
    @DisplayName(
        "should throw NotFoundException when notification does not exist"
    )
    void markAsRead_whenNotFound_throwsNotFoundException() {
        UUID notificationId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(notificationRepository.findById(notificationId)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> service.markAsRead(notificationId, callerId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Notification not found.");

        verify(notificationRepository, never()).save(any());
        verify(realtimeEventPublisher, never()).publishToUser(any(), any());
    }

    @Test
    @DisplayName(
        "should not fail transaction if SSE publishing throws an exception"
    )
    void markAsRead_whenSsePublishThrowsException_logsWarningAndCompletesSuccessfully() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        InAppNotification notification = InAppNotification.builder()
            .id(notificationId)
            .userId(userId)
            .eventKey("WORKSHOP_RESCHEDULED")
            .isRead(false)
            .build();

        when(notificationRepository.findById(notificationId)).thenReturn(
            Optional.of(notification)
        );
        when(
            notificationRepository.save(any(InAppNotification.class))
        ).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.countUnreadByUserId(userId)).thenReturn(1L);

        doThrow(new RuntimeException("Redis connection refused"))
            .when(realtimeEventPublisher)
            .publishToUser(eq(userId), any());

        MarkNotificationReadResult result = service.markAsRead(
            notificationId,
            userId
        );

        assertThat(result.notification().isRead()).isTrue();
        assertThat(result.unreadCount()).isEqualTo(1L);
        verify(notificationRepository).save(notification);
    }
}
