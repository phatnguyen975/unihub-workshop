package com.unihub.workshop.notifications.application.ports.out;

import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.util.UUID;

public interface NotificationRepository {
    /**
     * Counts all unread notifications for a specific user.
     *
     * @param userId the user's UUID
     * @return count of notifications where is_read is false
     */
    long countUnreadByUserId(UUID userId);

    /**
     * Finds a paginated slice of notifications for a specific user using keyset cursor pagination.
     *
     * @param userId the user's UUID
     * @param isRead optional filter for read status (null for all, true for read only, false for unread only)
     * @param cursor opaque keyset cursor token from previous page
     * @param size page window size
     * @return CursorPage of domain notifications
     */
    CursorPage<InAppNotification> findNotificationsByUserId(
        UUID userId,
        Boolean isRead,
        String cursor,
        int size
    );
}
