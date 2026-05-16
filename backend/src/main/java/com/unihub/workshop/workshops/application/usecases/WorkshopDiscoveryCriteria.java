package com.unihub.workshop.workshops.application.usecases;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkshopDiscoveryCriteria(
        String q,
        UUID organizerId,
        int page,
        int size,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        Integer minCapacity,
        String sort) {
}
