package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record ActiveSession(
    UUID id,
    String deviceInfo,
    String ipAddress,
    Instant createdAt,
    boolean isCurrentSession
) {}
