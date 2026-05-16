package com.unihub.workshop.workshops.application.dto;

import java.util.List;
import com.unihub.workshop.shared.web.PaginationMetadata;

public record AttendeePageResponse(
        List<AttendeeItemResponse> items,
        PaginationMetadata pagination) {
}
