package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import java.util.UUID;

public interface ListIngestionJobsUseCase {
    CursorPage<IngestionJobReport> execute(
        UUID organizationId,
        String cursor,
        Integer size
    );
}
