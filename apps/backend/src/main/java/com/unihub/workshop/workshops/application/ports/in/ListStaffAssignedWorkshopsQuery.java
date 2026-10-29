package com.unihub.workshop.workshops.application.ports.in;

import java.util.UUID;

public record ListStaffAssignedWorkshopsQuery(
    UUID staffUserId,
    String cursor,
    Integer size
) {
    public ListStaffAssignedWorkshopsQuery {
        if (staffUserId == null) {
            throw new IllegalArgumentException("staffUserId must not be null");
        }
    }
}
