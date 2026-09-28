package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionItemResponse(
    UUID id,
    String deviceInfo,
    String ipAddress,
    Instant createdAt,
    boolean isCurrentSession
) {}
