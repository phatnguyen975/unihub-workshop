package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.iam.domain.model.User;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileResponse(
    UUID id,
    String email,
    String fullName,
    String role,
    String phoneNumber,
    String avatarUrl,
    Instant lastLoginAt,
    UUID organizationId,
    UUID studentRecordId
) {
    public static UserProfileResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new UserProfileResponse(
            user.id(),
            user.email(),
            user.fullName(),
            user.role() != null ? user.role().name() : null,
            user.phoneNumber(),
            user.avatarUrl(),
            user.lastLoginAt(),
            user.organizationId(),
            user.studentRecordId()
        );
    }
}
