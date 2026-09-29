package com.unihub.workshop.workshops.application.ports.in;

import java.util.UUID;

public record ListAssignedStaffQuery(
    UUID workshopId,
    UUID organizationId,
    String cursor,
    Integer size
) {
    public ListAssignedStaffQuery {
        if (size == null || size <= 0) {
            size = 20;
        } else if (size > 100) {
            size = 100;
        }
    }
}
