package com.unihub.workshop.workshops.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StaffAssignedToWorkshopEvent(
        UUID workshopId,
        UUID staffId,
        UUID organizerId,
        OffsetDateTime assignedAt) {
}
