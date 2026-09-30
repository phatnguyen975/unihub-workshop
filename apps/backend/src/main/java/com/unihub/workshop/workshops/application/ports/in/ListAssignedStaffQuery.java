package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.exception.BadRequestException;
import java.util.UUID;

public record ListAssignedStaffQuery(
    UUID workshopId,
    UUID organizationId,
    String cursor,
    Integer size
) {
    public ListAssignedStaffQuery {
        if (size == null) {
            size = 20;
        } else if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
    }
}
