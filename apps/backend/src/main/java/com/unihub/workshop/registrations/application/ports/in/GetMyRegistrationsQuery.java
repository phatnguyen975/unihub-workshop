package com.unihub.workshop.registrations.application.ports.in;

import java.util.UUID;

public record GetMyRegistrationsQuery(
    UUID studentUserId,
    String status,
    String cursor,
    Integer size
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public GetMyRegistrationsQuery {
        if (studentUserId == null) {
            throw new IllegalArgumentException(
                "studentUserId must not be null"
            );
        }
    }

    /**
     * Resolves the page size clamped to range [1, 100] with default 20.
     */
    public int resolvedSize() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
