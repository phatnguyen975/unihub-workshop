package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryQuery;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryUseCase;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationRawCounts;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationSeatCounts;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetWorkshopRegistrationSummaryUseCaseImpl
    implements GetWorkshopRegistrationSummaryUseCase
{

    private final RegistrationRepository registrationRepository;
    private final WorkshopApi workshopApi;

    @Override
    @Transactional(readOnly = true)
    public WorkshopRegistrationSeatCounts getSummary(
        GetWorkshopRegistrationSummaryQuery query
    ) {
        log.debug(
            "Retrieving registration summary for workshop [{}] by org [{}]",
            query.workshopId(),
            query.callerOrganizationId()
        );

        // 1. Workshop existence check
        WorkshopRegistrationDetailsDto workshop = workshopApi
            .getWorkshopForRegistration(query.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found with ID: " + query.workshopId()
                )
            );

        // 2. Tenant isolation check: workshop must belong to caller's org
        if (!query.callerOrganizationId().equals(workshop.organizationId())) {
            log.warn(
                "Access denied: caller org [{}] does not match workshop org [{}] for workshop [{}]",
                query.callerOrganizationId(),
                workshop.organizationId(),
                query.workshopId()
            );
            throw new ForbiddenException(
                "Forbidden: Workshop does not belong to caller's organization"
            );
        }

        // 3. Query aggregated raw counts from read replica
        WorkshopRegistrationRawCounts rawCounts =
            registrationRepository.getRegistrationRawCounts(query.workshopId());

        // 4. Compute effective capacity and fallback timestamp
        int capacity = workshop.capacity() != null ? workshop.capacity() : 0;
        Instant lastUpdatedAt = rawCounts
            .latestUpdatedAt()
            .orElse(
                workshop.startsAt() != null
                    ? workshop.startsAt()
                    : Instant.now()
            );

        // 5. Build and return calculated seat counts snapshot
        return WorkshopRegistrationSeatCounts.of(
            workshop.id(),
            capacity,
            rawCounts.confirmedCount(),
            rawCounts.pendingCount(),
            lastUpdatedAt
        );
    }
}
