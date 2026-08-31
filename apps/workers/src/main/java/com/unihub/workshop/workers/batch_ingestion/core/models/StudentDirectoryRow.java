package com.unihub.workshop.workers.batch_ingestion.core.models;

import java.util.UUID;

public record StudentDirectoryRow(
        UUID organizationId,
        String studentNumber,
        String email,
        String fullName,
        String faculty) {
}
