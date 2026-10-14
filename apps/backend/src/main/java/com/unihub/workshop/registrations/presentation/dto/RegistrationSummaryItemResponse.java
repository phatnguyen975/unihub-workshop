package com.unihub.workshop.registrations.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrationSummaryItemResponse(
    UUID id,
    UUID workshopId,
    String workshopTitle,
    Instant workshopStartsAt,
    Instant workshopEndsAt,
    String status,
    Instant createdAt
) {}
