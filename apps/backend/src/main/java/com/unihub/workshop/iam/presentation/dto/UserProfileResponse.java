package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
) {}
