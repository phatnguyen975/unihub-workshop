package com.unihub.workshop.registrations.contracts;

import com.unihub.workshop.shared.web.PaginationMetadata;
import java.util.List;

public record AttendeePage(
        List<AttendeeItem> items,
        PaginationMetadata pagination) {
}
