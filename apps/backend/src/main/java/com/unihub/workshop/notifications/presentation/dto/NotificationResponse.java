package com.unihub.workshop.notifications.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
    UUID id,
    String eventKey,
    Map<String, Object> payload,
    boolean isRead,
    Instant readAt,
    String actionUrl,
    Instant createdAt
) {}
