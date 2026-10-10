package com.unihub.workshop.notifications.application.ports.in;

public interface GetNotificationsUseCase {
    /**
     * Retrieves a paginated keyset slice of notifications for a user,
     * along with the user's total unread notification count.
     *
     * @param query the query containing userId, isRead filter, cursor, and page size
     * @return NotificationsResult containing the page slice and total unread count
     */
    NotificationsResult getNotifications(GetNotificationsQuery query);
}
