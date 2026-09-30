package com.unihub.workshop.workshops.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record AssignedStaff(
    UUID userId,
    String fullName,
    String email,
    String avatarUrl,
    String phoneNumber,
    Instant assignedAt
) {}
