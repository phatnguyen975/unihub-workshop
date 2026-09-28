package com.unihub.workshop.workshops.application.ports.out;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import java.util.Optional;
import java.util.UUID;

public interface IngestionJobReportRepository {
    CursorPage<IngestionJobReport> findByOrganizationId(
        UUID organizationId,
        String cursor,
        int size
    );

    Optional<IngestionJobReport> findByIdAndOrganizationIdFromPrimary(
        UUID id,
        UUID organizationId
    );
}
