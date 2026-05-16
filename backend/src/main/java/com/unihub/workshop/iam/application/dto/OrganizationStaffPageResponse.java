package com.unihub.workshop.iam.application.dto;

import java.util.List;

import com.unihub.workshop.shared.web.PaginationMetadata;

public record OrganizationStaffPageResponse(
        List<OrganizationStaffResponse> items,
        PaginationMetadata pagination) {
}
