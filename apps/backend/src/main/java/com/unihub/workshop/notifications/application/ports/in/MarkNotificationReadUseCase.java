package com.unihub.workshop.notifications.application.ports.in;

import java.util.UUID;

public interface MarkNotificationReadUseCase {
    /**
     * Marks a single notification as read if owned by the current user.
     *
     * @param notificationId target notification UUID
     * @param currentUserId  authenticated user UUID
     * @return result containing the updated notification and current unread count
     */
    MarkNotificationReadResult markAsRead(
        UUID notificationId,
        UUID currentUserId
    );
}
