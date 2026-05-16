package com.unihub.workshop.workshops.presentation.controller;

import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.web.ResponseFactory;
import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.workshops.application.usecases.WorkshopAnalyticsService;
import com.unihub.workshop.workshops.application.dto.AttendeePageResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopAnalyticsResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementPageResponse;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/workshops", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORGANIZER')")
public class WorkshopAnalyticsController {

    private final WorkshopAnalyticsService analyticsService;
    private final ResponseFactory responseFactory;

    @GetMapping(path = "/management")
    public ResponseEntity<ApiResponse<WorkshopManagementPageResponse>> listOrganizerWorkshops(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(name = "status", required = false) String status) {
        String userId = requireUserId();
        UseCaseResult<WorkshopManagementPageResponse> result = analyticsService.listOrganizerWorkshops(userId, page,
                size, status);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @GetMapping(path = "/{id}/analytics")
    public ResponseEntity<ApiResponse<WorkshopAnalyticsResponse>> getWorkshopAnalytics(@PathVariable("id") UUID id) {
        String userId = requireUserId();
        UseCaseResult<WorkshopAnalyticsResponse> result = analyticsService.getWorkshopAnalytics(userId, id);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @GetMapping(path = "/{id}/attendees")
    public ResponseEntity<ApiResponse<AttendeePageResponse>> getWorkshopAttendees(
            @PathVariable("id") UUID id,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(name = "status", required = false) String status) {
        String userId = requireUserId();
        UseCaseResult<AttendeePageResponse> result = analyticsService.getWorkshopAttendees(userId, id, page, size,
                status);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    private String requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new com.unihub.workshop.shared.exception.AppException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing authentication",
                    "unauthorized");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String subject && !subject.isBlank() && !"anonymousUser".equals(subject)) {
            return subject;
        }
        throw new com.unihub.workshop.shared.exception.AppException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Invalid authentication",
                "unauthorized");
    }
}
