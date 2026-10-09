package com.unihub.workshop.notifications.application.ports.in;

import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.util.List;

public record NotificationsResult(
    CursorPage<InAppNotification> page,
    long unreadCount
) {
    public List<InAppNotification> items() {
        return page != null ? page.items() : List.of();
    }

    public String nextCursor() {
        return page != null ? page.nextCursor() : null;
    }

    public boolean hasMore() {
        return page != null && page.hasMore();
    }
}
