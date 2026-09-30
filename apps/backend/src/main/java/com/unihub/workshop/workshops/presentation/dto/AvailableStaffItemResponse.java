package com.unihub.workshop.workshops.presentation.dto;

import java.util.UUID;
import lombok.Builder;

@Builder
public record AvailableStaffItemResponse(
    UUID userId,
    String fullName,
    String email,
    String avatarUrl,
    String phoneNumber,
    int activeAssignmentsCount
) {}
