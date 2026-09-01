package com.unihub.workshop.workshops.application.ports.out;

import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;

import java.util.Optional;
import java.util.UUID;

public interface IngestionJobReportRepository {
    CursorPage<IngestionJobReport> findByOrganizationId(UUID organizationId, String cursor, int limit);

    Optional<IngestionJobReport> findByIdAndOrganizationIdFromPrimary(UUID id, UUID organizationId);
}
