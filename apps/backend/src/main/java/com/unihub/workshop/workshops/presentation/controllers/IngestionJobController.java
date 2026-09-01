package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.workshops.application.usecases.GetRejectArtifactUrlUseCase;
import com.unihub.workshop.workshops.application.usecases.ListIngestionJobsUseCase;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse.CursorResponse;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse.IngestionJobItem;
import com.unihub.workshop.workshops.presentation.dto.RejectArtifactUrlResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/ingestion-jobs")
public class IngestionJobController {

    private final ListIngestionJobsUseCase listUseCase;
    private final GetRejectArtifactUrlUseCase getRejectArtifactUrlUseCase;

    public IngestionJobController(ListIngestionJobsUseCase listUseCase,
            GetRejectArtifactUrlUseCase getRejectArtifactUrlUseCase) {
        this.listUseCase = listUseCase;
        this.getRejectArtifactUrlUseCase = getRejectArtifactUrlUseCase;
    }

    @GetMapping
    public IngestionJobListResponse getIngestionJobs(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            Authentication authentication) {

        JwtAuthenticatedPrincipal principal = (JwtAuthenticatedPrincipal) authentication.getPrincipal();
        String orgIdStr = principal.org();
        if (orgIdStr == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing org claim in token");
        }
        UUID orgId = UUID.fromString(orgIdStr);

        CursorPage<IngestionJobReport> page;
        try {
            page = listUseCase.execute(orgId, cursor, size);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

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
        return new IngestionJobListResponse(items, cursorResponse);
    }

    @GetMapping("/{jobId}/reject-artifact")
    public ApiResponse<RejectArtifactUrlResponse> getRejectArtifactUrl(
            @PathVariable String jobId,
            Authentication authentication) {

        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }

        JwtAuthenticatedPrincipal principal = (JwtAuthenticatedPrincipal) authentication.getPrincipal();
        String orgIdStr = principal.org();
        if (orgIdStr == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing org claim in token");
        }
        UUID orgId = UUID.fromString(orgIdStr);

        try {
            var result = getRejectArtifactUrlUseCase.execute(jobUuid, orgId);
            RejectArtifactUrlResponse responseDto = new RejectArtifactUrlResponse(
                    result.jobId(),
                    result.artifactType(),
                    result.downloadUrl(),
                    result.expiresAt());
            return new ApiResponse<>(responseDto, "Pre-signed download URL generated");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
