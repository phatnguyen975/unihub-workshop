package com.unihub.workshop.workshops.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkshopPublishedEvent(
        UUID workshopId,
        UUID organizerId,
        String title,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String location,
        int capacityTotal,
        OffsetDateTime publishedAt) {
}
