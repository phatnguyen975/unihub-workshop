package com.unihub.workshop.attendances.presentation.controllers;

import com.unihub.workshop.attendances.application.ports.in.GetAttendanceSummaryUseCase;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSummaryResponse;
import com.unihub.workshop.attendances.presentation.mapper.AttendancePresentationMapper;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttendanceSummaryController {

    private final GetAttendanceSummaryUseCase useCase;
    private final AttendancePresentationMapper mapper;

    @GetMapping("/api/v1/workshops/{workshopId}/attendances/summary")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ApiResponse<AttendanceSummaryResponse> getSummary(
        @PathVariable UUID workshopId,
        Authentication authentication
    ) {
        SecurityContextUtils.verifyRole(authentication, "ORGANIZER");
        UUID organizationId = SecurityContextUtils.extractOrganizationId(
            authentication
        );
        return ApiResponse.success(
            mapper.toSummaryResponse(
                useCase.execute(
                    mapper.toSummaryQuery(workshopId, organizationId)
                )
            ),
            "Attendance summary retrieved"
        );
    }
}
