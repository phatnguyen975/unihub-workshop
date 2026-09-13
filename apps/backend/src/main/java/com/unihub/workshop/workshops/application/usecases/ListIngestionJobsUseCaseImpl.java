package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.workshops.application.ports.in.ListIngestionJobsUseCase;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListIngestionJobsUseCaseImpl implements ListIngestionJobsUseCase {

    private final IngestionJobReportRepository repository;

    public ListIngestionJobsUseCaseImpl(
        IngestionJobReportRepository repository
    ) {
        this.repository = repository;
    }

    public CursorPage<IngestionJobReport> execute(
        UUID organizationId,
        String cursor,
        Integer size
    ) {
        if (organizationId == null) {
            throw new BadRequestException("organizationId must not be null");
        }

        int limit = size == null ? 20 : size;
        if (limit < 1) {
            throw new BadRequestException("size must be at least 1");
        }
        if (limit > 100) {
            limit = 100;
        }

        return repository.findByOrganizationId(organizationId, cursor, limit);
    }
}
