package com.unihub.workshop.notifications.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnreadCountResponse(long unreadCount) {
    public static UnreadCountResponse of(long unreadCount) {
        return new UnreadCountResponse(unreadCount);
    }
}
