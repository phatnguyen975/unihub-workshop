package com.unihub.workshop.notifications.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.shared.pagination.CursorResponse;
import java.util.List;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationPageData(
    List<NotificationResponse> items,
    long unreadCount,
    CursorResponse cursor
) {
    public static NotificationPageData of(
        List<NotificationResponse> items,
        long unreadCount,
        String nextCursor,
        boolean hasMore
    ) {
        return new NotificationPageData(
            items != null ? items : List.of(),
            unreadCount,
            CursorResponse.of(nextCursor, hasMore)
        );
    }
}
