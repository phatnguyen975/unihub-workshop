package com.unihub.workshop.iam.application.dto;

import java.util.List;

public record OrganizationStaffPageResponse(
        List<OrganizationStaffResponse> items,
        PaginationMetadata pagination) {
}
