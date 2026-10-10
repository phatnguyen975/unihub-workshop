package com.unihub.workshop.notifications.application.usecases;

import com.unihub.workshop.notifications.application.ports.in.MarkAllNotificationsReadUseCase;
import com.unihub.workshop.notifications.application.ports.in.MarkAllReadResult;
import com.unihub.workshop.notifications.application.ports.in.NotificationReadEventPayload;
import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MarkAllNotificationsReadService
    implements MarkAllNotificationsReadUseCase
{

    private final NotificationRepository notificationRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Override
    public MarkAllReadResult markAllAsRead(UUID userId) {
        Instant now = Instant.now();
        int updatedCount = notificationRepository.markAllAsReadByUserId(
            userId,
            now
        );

        if (updatedCount > 0) {
            publishReadEvent(userId, now);
        }

        return new MarkAllReadResult(updatedCount, 0L);
    }

    private void publishReadEvent(UUID userId, Instant readAt) {
        try {
            NotificationReadEventPayload payload =
                NotificationReadEventPayload.forAll(readAt);
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
