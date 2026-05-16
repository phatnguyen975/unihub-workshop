package com.unihub.workshop.workshops.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssignedStaffResponse(
        UUID staffId,
        OffsetDateTime assignedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
