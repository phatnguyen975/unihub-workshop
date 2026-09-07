package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ActiveSession(
        UUID id,
        String deviceInfo,
        String ipAddress,
        Instant createdAt,
        boolean isCurrentSession) {

    public ActiveSession withCurrentSession(boolean current) {
        return new ActiveSession(id, deviceInfo, ipAddress, createdAt, current);
    }
}
