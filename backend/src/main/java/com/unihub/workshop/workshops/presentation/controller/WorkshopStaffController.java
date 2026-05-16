package com.unihub.workshop.workshops.presentation.controller;

import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.web.ResponseFactory;
import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.workshops.application.dto.AssignStaffRequest;
import com.unihub.workshop.workshops.application.usecases.WorkshopStaffService;
import com.unihub.workshop.iam.application.dto.OrganizationStaffPageResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/workshops", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORGANIZER')")
public class WorkshopStaffController {

    private final WorkshopStaffService staffService;
    private final ResponseFactory responseFactory;

    @GetMapping("/{id}/staff/available")
    public ResponseEntity<ApiResponse<OrganizationStaffPageResponse>> getAvailableStaff(
            @PathVariable("id") UUID id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        String userId = requireUserId();
        UseCaseResult<OrganizationStaffPageResponse> result = staffService.getAvailableStaff(userId, id,
                PageRequest.of(page, size));
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @PostMapping(path = "/{id}/staff", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<WorkshopManagementFullResponse>> assignStaff(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignStaffRequest request) {
        String userId = requireUserId();
        UseCaseResult<WorkshopManagementFullResponse> result = staffService.assignStaff(userId, id, request);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @DeleteMapping("/{id}/staff/{staffId}")
    public ResponseEntity<ApiResponse<WorkshopManagementFullResponse>> removeStaff(
            @PathVariable("id") UUID id,
            @PathVariable("staffId") UUID staffId,
            @RequestParam(name = "version") int version) {
        String userId = requireUserId();
        UseCaseResult<WorkshopManagementFullResponse> result = staffService.removeStaff(userId, id, staffId, version);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    private String requireUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new com.unihub.workshop.shared.exception.AppException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing authentication", "unauthorized");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof String subject && !subject.isBlank() && !"anonymousUser".equals(subject)) {
            return subject;
        }
        throw new com.unihub.workshop.shared.exception.AppException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Invalid authentication", "unauthorized");
    }
}
