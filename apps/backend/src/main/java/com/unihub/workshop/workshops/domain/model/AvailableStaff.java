package com.unihub.workshop.workshops.domain.model;

import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record AvailableStaff(
    UUID userId,
    String fullName,
    String email,
    String avatarUrl,
    String phoneNumber,
    int activeAssignmentsCount
) {}
