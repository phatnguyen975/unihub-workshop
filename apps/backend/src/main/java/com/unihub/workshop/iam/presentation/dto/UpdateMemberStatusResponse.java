package com.unihub.workshop.iam.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record UpdateMemberStatusResponse(
    UUID userId,
    boolean isActive,
    Instant updatedAt
) {}
