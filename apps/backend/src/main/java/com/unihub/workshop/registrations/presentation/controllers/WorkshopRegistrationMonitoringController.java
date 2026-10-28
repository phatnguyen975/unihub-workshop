package com.unihub.workshop.registrations.presentation.controllers;

import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryQuery;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryUseCase;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationSeatCounts;
import com.unihub.workshop.registrations.presentation.dto.WorkshopRegistrationSummaryResponse;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/workshops/{workshopId}/registrations")
public class WorkshopRegistrationMonitoringController {

    private final GetWorkshopRegistrationSummaryUseCase getWorkshopRegistrationSummaryUseCase;

    /**
     * Retrieves the registration summary and seat counts for the specified workshop.
     *
     * @param workshopId unique identifier of the workshop
     * @param authentication active security context
     * @return 200 OK with seat counts snapshot
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ApiResponse<WorkshopRegistrationSummaryResponse> getSummary(
        @PathVariable UUID workshopId,
        Authentication authentication
    ) {
        if (authentication == null) {
            throw new UnauthorizedException("Authentication required");
        }

        UUID callerOrgId = SecurityContextUtils.extractOrganizationId(
            authentication
        );

        GetWorkshopRegistrationSummaryQuery query =
            new GetWorkshopRegistrationSummaryQuery(workshopId, callerOrgId);

        WorkshopRegistrationSeatCounts summary =
            getWorkshopRegistrationSummaryUseCase.getSummary(query);

        WorkshopRegistrationSummaryResponse response =
            new WorkshopRegistrationSummaryResponse(
                summary.workshopId(),
                summary.capacity(),
                summary.confirmedCount(),
                summary.pendingCount(),
                summary.availableSeats(),
                summary.occupancyPercentage(),
                summary.lastUpdatedAt()
            );

        return ApiResponse.success(response, "Registration summary retrieved");
    }
}
