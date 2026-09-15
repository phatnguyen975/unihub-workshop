package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
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
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Page(List<MemberSummaryResponse> items, Cursor cursor) {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Cursor(String nextCursor, boolean hasMore) {}
}
