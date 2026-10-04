package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AvailableStaffItemResponse(
    UUID userId,
    String fullName,
    String email,
    String avatarUrl,
    String phoneNumber,
    int activeAssignmentsCount
) {}
