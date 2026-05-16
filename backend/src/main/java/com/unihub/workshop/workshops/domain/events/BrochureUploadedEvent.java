package com.unihub.workshop.workshops.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BrochureUploadedEvent(
        UUID workshopId,
        UUID organizerId,
        String fileUrl,
        String fileName,
        long fileSizeBytes,
        OffsetDateTime uploadedAt) {
}
