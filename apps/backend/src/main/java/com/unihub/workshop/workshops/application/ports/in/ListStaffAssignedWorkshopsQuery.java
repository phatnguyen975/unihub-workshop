package com.unihub.workshop.workshops.application.ports.in;

import java.util.UUID;

/**
 * Query for retrieving paginated workshops assigned to a check-in staff member,
 * with optional multi-criteria filters.
 *
 * @param staffUserId the authenticated staff user ID (mandatory)
 * @param cursor the keyset cursor token (optional)
 * @param size page size (optional, 1..100)
 * @param status workshop status filter (PUBLISHED or COMPLETED only)
 * @param from minimum start timestamp ISO-8601 UTC string (inclusive)
 * @param to maximum start timestamp ISO-8601 UTC string (inclusive)
 * @param q keyword substring search against title, slug, and location
 */
public record ListStaffAssignedWorkshopsQuery(
    UUID staffUserId,
    String cursor,
    Integer size,
    String status,
    String from,
    String to,
    String q
) {
    public ListStaffAssignedWorkshopsQuery(
        UUID staffUserId,
        String cursor,
        Integer size
    ) {
        this(staffUserId, cursor, size, null, null, null, null);
    }

    public ListStaffAssignedWorkshopsQuery {
        if (staffUserId == null) {
            throw new IllegalArgumentException("staffUserId must not be null");
        }
    }
}
