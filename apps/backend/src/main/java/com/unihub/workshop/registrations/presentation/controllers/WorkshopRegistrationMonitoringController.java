package com.unihub.workshop.registrations.presentation.controllers;

import com.unihub.workshop.registrations.application.ports.in.GetWorkshopAttendeeRosterQuery;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopAttendeeRosterUseCase;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryQuery;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryUseCase;
import com.unihub.workshop.registrations.domain.model.WorkshopAttendeeRosterItem;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationSeatCounts;
import com.unihub.workshop.registrations.presentation.dto.WorkshopAttendeeRosterResponse;
import com.unihub.workshop.registrations.presentation.dto.WorkshopRegistrationSummaryResponse;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/workshops/{workshopId}/registrations")
public class WorkshopRegistrationMonitoringController {

    private final GetWorkshopRegistrationSummaryUseCase getWorkshopRegistrationSummaryUseCase;
    private final GetWorkshopAttendeeRosterUseCase getWorkshopAttendeeRosterUseCase;

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
            WorkshopRegistrationSummaryResponse.of(summary);

        return ApiResponse.success(response, "Registration summary retrieved");
    }

    /**
     * Retrieves the cursor-paginated attendee roster for the specified workshop.
     *
     * @param workshopId unique identifier of the workshop
     * @param cursor optional keyset cursor token
     * @param size optional page size limit (1..100)
     * @param status optional registration status filter
     * @param authentication active security context
     * @return 200 OK with attendee roster entries and pagination cursor
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ApiResponse<WorkshopAttendeeRosterResponse> getRoster(
        @PathVariable UUID workshopId,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String status,
        Authentication authentication
    ) {
        if (authentication == null) {
            throw new UnauthorizedException("Authentication required");
        }

        UUID callerOrgId = SecurityContextUtils.extractOrganizationId(
            authentication
        );

        String normalizedCursor =
            cursor != null && !cursor.isBlank() ? cursor.trim() : null;
        String normalizedStatus =
            status != null && !status.isBlank() ? status.trim() : null;

        GetWorkshopAttendeeRosterQuery query =
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                callerOrgId,
                normalizedStatus,
                normalizedCursor,
                size
            );

        CursorPage<WorkshopAttendeeRosterItem> page =
            getWorkshopAttendeeRosterUseCase.getRoster(query);

        WorkshopAttendeeRosterResponse response =
            WorkshopAttendeeRosterResponse.of(page);

        return ApiResponse.success(response, "Attendee roster retrieved");
    }
}
