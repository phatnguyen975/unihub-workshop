package com.unihub.workshop.iam.presentation.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record UpdateMemberStatusResponse(
    UUID userId,
    boolean isActive,
    Instant updatedAt
) {}
