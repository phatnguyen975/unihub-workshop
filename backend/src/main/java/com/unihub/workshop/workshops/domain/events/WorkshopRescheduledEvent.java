package com.unihub.workshop.workshops.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkshopRescheduledEvent(
        UUID workshopId,
        UUID organizerId,
        String title,
        OffsetDateTime previousStartTime,
        OffsetDateTime newStartTime,
        OffsetDateTime previousEndTime,
        OffsetDateTime newEndTime,
        String previousLocation,
        String newLocation,
        OffsetDateTime rescheduledAt) {
}
