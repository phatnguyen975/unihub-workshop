package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.iam.domain.model.User;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberDetailResponse(
    UUID id,
    UUID organizationId,
    String email,
    String fullName,
    String role,
    String phoneNumber,
    String avatarUrl,
    boolean isActive,
    boolean isTemporaryPassword,
    UUID studentRecordId,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static MemberDetailResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new MemberDetailResponse(
            user.id(),
            user.organizationId(),
            user.email(),
            user.fullName(),
            user.role() != null ? user.role().name() : null,
            user.phoneNumber(),
            user.avatarUrl(),
            user.isActive(),
            user.isTemporaryPassword(),
            user.studentRecordId(),
            user.lastLoginAt(),
            user.createdAt(),
            user.updatedAt()
        );
    }
}
