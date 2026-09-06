package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.workshops.application.ports.in.GetRejectArtifactUrlUseCase;
import com.unihub.workshop.workshops.application.ports.in.ListIngestionJobsUseCase;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse.CursorResponse;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse.IngestionJobItem;
import com.unihub.workshop.workshops.presentation.dto.RejectArtifactUrlResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/ingestion-jobs")
@RequiredArgsConstructor
public class IngestionJobController {

    private final ListIngestionJobsUseCase listUseCase;
    private final GetRejectArtifactUrlUseCase getRejectArtifactUrlUseCase;

    @GetMapping
    public ApiResponse<IngestionJobListResponse> getIngestionJobs(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            Authentication authentication) {

        UUID orgId = extractOrganizationId(authentication);

        CursorPage<IngestionJobReport> page = listUseCase.execute(orgId, cursor, size);

        List<IngestionJobItem> items = page.items().stream()
                .map(report -> new IngestionJobItem(
                        report.id(),
                        report.sourceFileKey(),
                        report.outcome().name(),
                        report.processedRowCount(),
                        report.rejectedRowCount(),
                        report.completedAt(),
                        report.isArtifactAvailable()))
                .collect(Collectors.toList());

        CursorResponse cursorResponse = new CursorResponse(page.nextCursor(), page.hasMore());
        return ApiResponse.success(new IngestionJobListResponse(items, cursorResponse),
                "Ingestion job reports retrieved");
    }

    @GetMapping("/{jobId}/reject-artifact")
    public ApiResponse<RejectArtifactUrlResponse> getRejectArtifactUrl(
            @PathVariable String jobId,
            Authentication authentication) {

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid UUID format");
        }

        UUID orgId = extractOrganizationId(authentication);

        var result = getRejectArtifactUrlUseCase.execute(jobUuid, orgId);
        RejectArtifactUrlResponse responseDto = new RejectArtifactUrlResponse(
                result.jobId(),
                result.artifactType(),
                result.downloadUrl(),
                result.expiresAt());
        return ApiResponse.success(responseDto, "Pre-signed download URL generated");
    }

    private UUID extractOrganizationId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtAuthenticatedPrincipal principal)) {
            throw new ForbiddenException("Missing or invalid authentication principal");
        }
        String orgIdStr = principal.org();
        if (orgIdStr == null || orgIdStr.isBlank()) {
            throw new ForbiddenException("Missing org claim in token");
        }
        try {
            return UUID.fromString(orgIdStr);
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Invalid org claim in token");
        }
    }
}
