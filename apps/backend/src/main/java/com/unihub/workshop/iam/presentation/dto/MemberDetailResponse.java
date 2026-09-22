package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
) {}
