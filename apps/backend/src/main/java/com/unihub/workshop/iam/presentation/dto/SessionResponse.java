package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(List<SessionItem> items, SessionCursor cursor) {
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionItem(
        UUID id,
        String deviceInfo,
        String ipAddress,
        Instant createdAt,
        boolean isCurrentSession
    ) {}
}
