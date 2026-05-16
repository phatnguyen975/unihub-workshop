package com.unihub.workshop.workshops.presentation.controller;

import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.web.ResponseFactory;
import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.workshops.application.dto.ChangeStatusRequest;
import com.unihub.workshop.workshops.application.dto.CreateWorkshopRequest;
import com.unihub.workshop.workshops.application.dto.RescheduleWorkshopRequest;
import com.unihub.workshop.workshops.application.dto.UpdateWorkshopRequest;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse;
import com.unihub.workshop.workshops.application.usecases.WorkshopManagementService;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/workshops", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ORGANIZER')")
public class WorkshopManagementController {

    private final WorkshopManagementService managementService;
    private final ResponseFactory responseFactory;

    @PostMapping
    public ResponseEntity<ApiResponse<WorkshopManagementResponse>> createWorkshop(
            @Valid @RequestBody CreateWorkshopRequest request) {
        String userId = requireUserId();
        UseCaseResult<WorkshopManagementResponse> result = managementService.createWorkshop(userId, request);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkshopManagementFullResponse>> updateWorkshop(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateWorkshopRequest request) {
        String userId = requireUserId();
        UseCaseResult<WorkshopManagementFullResponse> result = managementService.updateWorkshop(userId, id, request);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<WorkshopManagementFullResponse>> rescheduleWorkshop(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RescheduleWorkshopRequest request) {
        String userId = requireUserId();
        UseCaseResult<WorkshopManagementFullResponse> result = managementService.rescheduleWorkshop(userId, id,
                request);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<WorkshopManagementFullResponse>> changeStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ChangeStatusRequest request) {
        String userId = requireUserId();
        UseCaseResult<WorkshopManagementFullResponse> result = managementService.changeStatus(userId, id, request);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkshopManagementFullResponse>> deleteWorkshop(
            @PathVariable("id") UUID id,
            @RequestParam(name = "version") int version) {
        String userId = requireUserId();
        UseCaseResult<WorkshopManagementFullResponse> result = managementService.deleteWorkshop(userId, id, version);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @GetMapping("/{id}/management")
    public ResponseEntity<ApiResponse<WorkshopManagementFullResponse>> getWorkshopManagement(
            @PathVariable("id") UUID id) {
        String userId = requireUserId();
        UseCaseResult<WorkshopManagementFullResponse> result = managementService.getWorkshopForManagement(userId, id);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    private String requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Missing authentication",
                    "unauthorized");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String subject && !subject.isBlank() && !"anonymousUser".equals(subject)) {
            return subject;
        }
        throw new AppException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid authentication",
                "unauthorized");
    }
}
