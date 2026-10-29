package com.unihub.workshop.registrations.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of an attendee roster item for workshop registration monitoring.
 *
 * @param id registration unique identifier
 * @param student summary profile of the registered student
 * @param status registration lifecycle status
 * @param price price charged at registration time
 * @param currency ISO currency code
 * @param registeredAt registration creation timestamp
 */
public record WorkshopAttendeeRosterItem(
    UUID id,
    StudentProfileSummary student,
    RegistrationStatus status,
    BigDecimal price,
    String currency,
    Instant registeredAt
) {
    /**
     * Immutable value object holding student profile summary.
     *
     * @param id student user account identifier
     * @param fullName student full name from IAM
     * @param studentNumber student institutional registration number
     * @param email student institutional email address
     */
    public record StudentProfileSummary(
        UUID id,
        String fullName,
        String studentNumber,
        String email
    ) {}
}
