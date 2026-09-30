package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.exception.BadRequestException;
import java.util.UUID;

public record ListAvailableStaffQuery(
    UUID workshopId,
    UUID organizationId,
    String q,
    String cursor,
    Integer size
) {
    public ListAvailableStaffQuery {
        if (q != null) {
            q = q.trim();
            if (q.length() > 100) {
                throw new BadRequestException(
                    "Search query 'q' must not exceed 100 characters"
                );
            }
            if (q.isEmpty()) {
                q = null;
            }
        }
        if (size == null) {
            size = 20;
        } else if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
    }
}
