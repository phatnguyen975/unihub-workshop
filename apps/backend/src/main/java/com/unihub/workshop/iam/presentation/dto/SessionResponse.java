package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(
        List<SessionItem> items,
        SessionCursor cursor) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionItem(
            UUID id,
            String deviceInfo,
            String ipAddress,
            Instant createdAt,
            @JsonProperty("isCurrentSession") boolean isCurrentSession) {
    }
}
