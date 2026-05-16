package com.unihub.workshop.workshops.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkshopCanceledEvent(
        UUID workshopId,
        UUID organizerId,
        String title,
        String cancelReason,
        OffsetDateTime canceledAt) {
}
