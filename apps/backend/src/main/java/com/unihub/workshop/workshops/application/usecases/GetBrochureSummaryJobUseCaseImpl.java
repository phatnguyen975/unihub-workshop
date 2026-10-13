package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.application.ports.in.GetBrochureSummaryJobQuery;
import com.unihub.workshop.workshops.application.ports.in.GetBrochureSummaryJobUseCase;
import com.unihub.workshop.workshops.application.ports.out.AiSummaryJobRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopBrochureRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.AiSummaryJob;
import com.unihub.workshop.workshops.domain.model.Workshop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetBrochureSummaryJobUseCaseImpl
    implements GetBrochureSummaryJobUseCase
{

    private final WorkshopRepository workshopRepository;
    private final WorkshopBrochureRepository brochureRepository;
    private final AiSummaryJobRepository jobRepository;

    @Override
    @Transactional(readOnly = true)
    public AiSummaryJob getSummaryJob(GetBrochureSummaryJobQuery query) {
        Workshop workshop = workshopRepository
            .findById(query.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found with id: " + query.workshopId()
                )
            );

        if (!workshop.organizationId().equals(query.organizationId())) {
            throw new ForbiddenException(
                "You do not have permission to view summary jobs for this workshop."
            );
        }

        brochureRepository
            .findActiveByWorkshopId(query.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "No active brochure found for workshop: " +
                        query.workshopId()
                )
            );

        return jobRepository
            .findLatestByWorkshopId(query.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "No summary job found for workshop: " + query.workshopId()
                )
            );
    }
}
