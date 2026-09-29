package com.unihub.workshop.workshops.presentation.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Presentation DTO for an item in the assigned staff roster.
 */
@Builder
public record AssignedStaffItemResponse(
    UUID userId,
    String fullName,
    String email,
    String avatarUrl,
    String phoneNumber,
    Instant assignedAt
) {}
