package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.workshops.application.ports.in.GetRejectArtifactUrlUseCase;
import com.unihub.workshop.workshops.application.ports.in.ListIngestionJobsUseCase;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse.CursorResponse;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse.IngestionJobItem;
import com.unihub.workshop.workshops.presentation.dto.RejectArtifactUrlResponse;
import com.unihub.workshop.workshops.presentation.mapper.IngestionJobPresentationMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ingestion-jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORGANIZER')")
public class IngestionJobController {

    private final ListIngestionJobsUseCase listUseCase;
    private final GetRejectArtifactUrlUseCase getRejectArtifactUrlUseCase;
    private final IngestionJobPresentationMapper presentationMapper;

    @GetMapping
    public ApiResponse<IngestionJobListResponse> getIngestionJobs(
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false, defaultValue = "20") Integer size,
        Authentication authentication
    ) {
        UUID orgId = SecurityContextUtils.extractOrganizationId(authentication);

        CursorPage<IngestionJobReport> page = listUseCase.execute(
            orgId,
            cursor,
            size
        );

        List<IngestionJobItem> items = presentationMapper.toItemList(
            page.items()
        );
        CursorResponse cursorResponse = new CursorResponse(
            page.nextCursor(),
            page.hasMore()
        );

        return ApiResponse.success(
            new IngestionJobListResponse(items, cursorResponse),
            "Ingestion job reports retrieved"
        );
    }

    @GetMapping("/{jobId}/reject-artifact")
    public ApiResponse<RejectArtifactUrlResponse> getRejectArtifactUrl(
        @PathVariable String jobId,
        Authentication authentication
    ) {
        UUID jobUuid;
        try {
            jobUuid = UUID.fromString(jobId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid UUID format");
        }

        UUID orgId = SecurityContextUtils.extractOrganizationId(authentication);

        var result = getRejectArtifactUrlUseCase.execute(jobUuid, orgId);
        RejectArtifactUrlResponse responseDto =
            presentationMapper.toRejectArtifactUrlResponse(result);
        return ApiResponse.success(
            responseDto,
            "Pre-signed download URL generated"
        );
    }
}
