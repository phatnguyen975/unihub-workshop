package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.workshops.application.dto.WorkshopDiscoveryPageResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopPublicDetailsResponse;
import java.util.UUID;

public interface WorkshopPublicUseCase {

    UseCaseResult<WorkshopDiscoveryPageResponse> discoverWorkshops(WorkshopDiscoveryCriteria criteria);

    UseCaseResult<WorkshopPublicDetailsResponse> getWorkshopDetails(UUID workshopId);
}
