package com.unihub.workshop.notifications.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MarkAllReadResponse(int updatedCount, long unreadCount) {
    public static MarkAllReadResponse of(int updatedCount, long unreadCount) {
        return new MarkAllReadResponse(updatedCount, unreadCount);
    }
}
