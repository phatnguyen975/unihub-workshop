package com.unihub.workshop.registrations.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RegistrationSummary(
    UUID id,
    UUID workshopId,
    String workshopTitle,
    Instant workshopStartsAt,
    Instant workshopEndsAt,
    RegistrationStatus status,
    Instant createdAt
) {}
