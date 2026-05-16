package com.unihub.workshop.workshops.application.dto;

import java.util.List;

import com.unihub.workshop.shared.web.PaginationMetadata;

public record WorkshopDiscoveryPageResponse(
        List<WorkshopDiscoveryItemResponse> items,
        PaginationMetadata pagination) {
}
