package com.unihub.workshop.notifications.application.ports.in;

import java.util.UUID;

public interface GetUnreadCountUseCase {
    /**
     * Retrieves the total count of unread notifications for a given user.
     *
     * @param userId the user's UUID
     * @return unread count (>= 0)
     */
    long getUnreadCount(UUID userId);
}
