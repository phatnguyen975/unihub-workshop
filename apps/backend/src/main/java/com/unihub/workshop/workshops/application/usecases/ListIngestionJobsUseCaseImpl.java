package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListIngestionJobsUseCase;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListIngestionJobsUseCaseImpl implements ListIngestionJobsUseCase {

    private final IngestionJobReportRepository repository;

    public CursorPage<IngestionJobReport> execute(
        UUID organizationId,
        String cursor,
        Integer size
    ) {
        if (organizationId == null) {
            throw new BadRequestException("organizationId must not be null");
        }

        int pageSize = size == null ? 20 : size;
        if (pageSize < 1 || pageSize > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }

        return repository.findByOrganizationId(
            organizationId,
            cursor,
            pageSize
        );
    }
}
