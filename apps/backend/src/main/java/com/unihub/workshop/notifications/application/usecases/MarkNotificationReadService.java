package com.unihub.workshop.notifications.application.usecases;

import com.unihub.workshop.notifications.application.ports.in.MarkNotificationReadResult;
import com.unihub.workshop.notifications.application.ports.in.MarkNotificationReadUseCase;
import com.unihub.workshop.notifications.application.ports.in.NotificationReadEventPayload;
import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class MarkNotificationReadService
    implements MarkNotificationReadUseCase
{

    private final NotificationRepository notificationRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Override
    public MarkNotificationReadResult markAsRead(
        UUID notificationId,
        UUID currentUserId
    ) {
        InAppNotification notification = notificationRepository
            .findById(notificationId)
            .orElseThrow(() ->
                new NotFoundException("Notification not found.")
            );

        if (!notification.getUserId().equals(currentUserId)) {
            throw new ForbiddenException(
                "You do not have permission to access this notification."
            );
        }

        Instant now = Instant.now();
        boolean transitioned = notification.markAsRead(now);

        long unreadCount;
        if (transitioned) {
            notificationRepository.save(notification);
            unreadCount = notificationRepository.countUnreadByUserId(
                currentUserId
            );
            publishReadEvent(currentUserId, notificationId, unreadCount, now);
        } else {
            unreadCount = notificationRepository.countUnreadByUserId(
                currentUserId
            );
        }

        return new MarkNotificationReadResult(notification, unreadCount);
    }

    private void publishReadEvent(
        UUID userId,
        UUID notificationId,
        long unreadCount,
        Instant readAt
    ) {
        try {
            NotificationReadEventPayload payload =
                NotificationReadEventPayload.forSingle(
                    notificationId,
                    unreadCount,
                    readAt
                );
            RealtimeEvent<NotificationReadEventPayload> event =
                RealtimeEvent.of(RealtimeEventType.NOTIFICATION_READ, payload);
            realtimeEventPublisher.publishToUser(userId, event);
        } catch (Exception ex) {
            log.warn(
                "Failed to publish notification.read event for user {}",
                userId,
                ex
            );
        }
    }
}
