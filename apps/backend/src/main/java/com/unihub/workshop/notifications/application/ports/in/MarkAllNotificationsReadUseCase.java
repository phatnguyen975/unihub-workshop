package com.unihub.workshop.notifications.application.ports.in;

import java.util.UUID;

public interface MarkAllNotificationsReadUseCase {
    /**
     * Marks all unread notifications for a user as read.
     *
     * @param userId authenticated user UUID
     * @return result containing number of updated records and new unread count (always 0)
     */
    MarkAllReadResult markAllAsRead(UUID userId);
}
