package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.workshops.application.usecases.ListIngestionJobsUseCase;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse.CursorResponse;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse.IngestionJobItem;
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

    public IngestionJobController(ListIngestionJobsUseCase listUseCase) {
        this.listUseCase = listUseCase;
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
}
