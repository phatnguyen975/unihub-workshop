package com.unihub.workshop.notifications.application.ports.out;

import java.util.UUID;

public interface NotificationRepository {
    /**
     * Counts all unread notifications for a specific user.
     *
     * @param userId the user's UUID
     * @return count of notifications where is_read is false
     */
    long countUnreadByUserId(UUID userId);
}
