package com.unihub.workshop.workshops.presentation.controller;

import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.web.ResponseFactory;
import com.unihub.workshop.workshops.application.dto.WorkshopDiscoveryPageResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopPublicDetailsResponse;
import com.unihub.workshop.workshops.application.usecases.WorkshopDiscoveryCriteria;
import com.unihub.workshop.workshops.application.usecases.WorkshopPublicUseCase;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/workshops", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class WorkshopPublicController {

    private final WorkshopPublicUseCase workshopPublicUseCase;
    private final ResponseFactory responseFactory;

    @GetMapping
    public ResponseEntity<ApiResponse<WorkshopDiscoveryPageResponse>> listWorkshops(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "organizerId", required = false) UUID organizerId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime endDate,
            @RequestParam(name = "minCapacity", required = false) @Min(0) Integer minCapacity,
            @RequestParam(name = "sort", required = false) String sort) {
        WorkshopDiscoveryCriteria criteria = new WorkshopDiscoveryCriteria(
                q,
                organizerId,
                page,
                size,
                startDate,
                endDate,
                minCapacity,
                sort);
        var result = workshopPublicUseCase.discoverWorkshops(criteria);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkshopPublicDetailsResponse>> getWorkshopDetails(
            @PathVariable("id") UUID workshopId) {
        var result = workshopPublicUseCase.getWorkshopDetails(workshopId);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }
}
