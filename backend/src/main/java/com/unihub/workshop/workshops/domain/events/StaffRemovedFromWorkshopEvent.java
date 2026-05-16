package com.unihub.workshop.workshops.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StaffRemovedFromWorkshopEvent(
        UUID workshopId,
        UUID staffId,
        UUID organizerId,
        OffsetDateTime removedAt) {
}
