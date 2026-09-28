package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
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
) {}
