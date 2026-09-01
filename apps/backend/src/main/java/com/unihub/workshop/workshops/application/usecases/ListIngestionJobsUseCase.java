package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ListIngestionJobsUseCase {

    private final IngestionJobReportRepository repository;

    public ListIngestionJobsUseCase(IngestionJobReportRepository repository) {
        this.repository = repository;
    }

    public CursorPage<IngestionJobReport> execute(UUID organizationId, String cursor, Integer size) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId must not be null");
        }

        int limit = (size == null) ? 20 : size;
        if (limit < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        if (limit > 100) {
            limit = 100;
        }

        return repository.findByOrganizationId(organizationId, cursor, limit);
    }
}
