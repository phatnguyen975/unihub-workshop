package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.iam.domain.model.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberSummaryResponse(
    UUID id,
    String email,
    String fullName,
    String role,
    String phoneNumber,
    String avatarUrl,
    boolean isActive,
    boolean isTemporaryPassword,
    Instant lastLoginAt,
    Instant createdAt
) {
    public static MemberSummaryResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new MemberSummaryResponse(
            user.id(),
            user.email(),
            user.fullName(),
            user.role() != null ? user.role().name() : null,
            user.phoneNumber(),
            user.avatarUrl(),
            user.isActive(),
            user.isTemporaryPassword(),
            user.lastLoginAt(),
            user.createdAt()
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Page(List<MemberSummaryResponse> items, Cursor cursor) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Cursor(String nextCursor, boolean hasMore) {}
}
