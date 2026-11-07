package com.unihub.workshop.attendances.presentation.controllers;

import com.unihub.workshop.attendances.application.ports.in.GetAttendanceSummaryUseCase;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSummaryResponse;
import com.unihub.workshop.attendances.presentation.mapper.AttendancePresentationMapper;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AttendanceSummaryController {

    private final GetAttendanceSummaryUseCase useCase;
    private final AttendancePresentationMapper mapper;

    @GetMapping("/api/v1/workshops/{workshopId}/attendances/summary")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'STAFF')")
    public ApiResponse<AttendanceSummaryResponse> getSummary(
        @PathVariable UUID workshopId,
        Authentication authentication
    ) {
        try {
            return retrieveSummary(workshopId, authentication);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.error("Unexpected failure retrieving attendance summary", ex);
            throw new InternalServerException(
                "An unexpected system error occurred.",
                ex
            );
        }
    }

    private ApiResponse<AttendanceSummaryResponse> retrieveSummary(
        UUID workshopId,
        Authentication authentication
    ) {
        String role = SecurityContextUtils.extractRole(
            authentication
        ).toUpperCase(Locale.ROOT);
        if (!"ORGANIZER".equals(role) && !"STAFF".equals(role)) {
            throw new ForbiddenException("Access denied");
        }
        UUID userId = SecurityContextUtils.extractUserId(authentication);
        UUID organizationId = "ORGANIZER".equals(role)
            ? SecurityContextUtils.extractOrganizationId(authentication)
            : null;
        return ApiResponse.success(
            mapper.toSummaryResponse(
                useCase.execute(
                    mapper.toSummaryQuery(
                        workshopId,
                        organizationId,
                        userId,
                        role
                    )
                )
            ),
            "Attendance summary retrieved"
        );
    }
}
