package com.unihub.workshop.iam.presentation.dto;

import com.unihub.workshop.iam.domain.model.User;
import java.time.Instant;
import java.util.UUID;

public record UpdateMemberStatusResponse(
    UUID userId,
    boolean isActive,
    Instant updatedAt
) {
    public static UpdateMemberStatusResponse from(User user) {
        return new UpdateMemberStatusResponse(
            user.id(),
            user.isActive(),
            user.updatedAt() != null ? user.updatedAt() : Instant.now()
        );
    }
}
