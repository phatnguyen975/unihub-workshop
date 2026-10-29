package com.unihub.workshop.workshops.domain.model;

import java.time.Instant;
import java.util.UUID;

public record StaffAssignedWorkshop(
    UUID id,
    String title,
    String slug,
    String description,
    String coverImageUrl,
    String location,
    Instant startsAt,
    Instant endsAt,
    WorkshopStatus status,
    String organizationName,
    Instant assignedAt
) {
    public StaffAssignedWorkshop {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                "title must not be null or blank"
            );
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException(
                "slug must not be null or blank"
            );
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException(
                "location must not be null or blank"
            );
        }
        if (startsAt == null) {
            throw new IllegalArgumentException("startsAt must not be null");
        }
        if (endsAt == null) {
            throw new IllegalArgumentException("endsAt must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (organizationName == null || organizationName.isBlank()) {
            throw new IllegalArgumentException(
                "organizationName must not be null or blank"
            );
        }
        if (assignedAt == null) {
            throw new IllegalArgumentException("assignedAt must not be null");
        }
    }
}
