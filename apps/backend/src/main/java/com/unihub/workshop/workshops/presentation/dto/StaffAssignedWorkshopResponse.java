package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaffAssignedWorkshopResponse(
    UUID id,
    String title,
    String slug,
    String description,
    String coverImageUrl,
    String location,
    Instant startsAt,
    Instant endsAt,
    String status,
    String organizationName,
    Instant assignedAt
) {}
