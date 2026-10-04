package com.unihub.workshop.workshops.domain.model;

import java.time.Instant;
import java.util.UUID;

public record CompletedWorkshopDetails(
    UUID id,
    UUID organizationId,
    String title,
    String slug,
    Instant startsAt,
    Instant endsAt,
    Instant completedAt
) {}
