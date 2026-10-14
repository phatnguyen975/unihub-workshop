package com.unihub.workshop.workshops.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable DTO representing workshop registration eligibility and scheduling details for peer modules.
 *
 * @param id unique workshop ID
 * @param organizationId owning organization ID
 * @param title workshop title
 * @param slug SEO slug
 * @param status workshop lifecycle status (e.g., "PUBLISHED", "DRAFT", "CANCELED")
 * @param startsAt session start timestamp
 * @param endsAt session end timestamp
 * @param price admission price (0.00 for free)
 * @param capacity total attendee capacity
 * @param location venue or room name
 */
public record WorkshopRegistrationDetailsDto(
    UUID id,
    UUID organizationId,
    String title,
    String slug,
    String status,
    Instant startsAt,
    Instant endsAt,
    BigDecimal price,
    Integer capacity,
    String location
) {
    /**
     * Checks if the workshop is in published status.
     *
     * @return true if status equals "PUBLISHED", false otherwise
     */
    public boolean isPublished() {
        return "PUBLISHED".equalsIgnoreCase(status);
    }
}
