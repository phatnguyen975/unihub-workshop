package com.unihub.workshop.workshops.domain.model;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.FilterFingerprint;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Filter criteria for staff-assigned workshop provisioning queries.
 *
 * <p>Enforces domain constraints on status values, timestamp validity and ordering,
 * and search keyword length. Generates canonical filter fingerprints for keyset pagination
 * cursor integrity checks.
 *
 * @param status workshop lifecycle status (PUBLISHED or COMPLETED only)
 * @param from minimum start timestamp (inclusive)
 * @param to maximum start timestamp (inclusive)
 * @param q keyword substring match against title, slug, and location
 */
public record StaffWorkshopFilter(
    WorkshopStatus status,
    Instant from,
    Instant to,
    String q
) {
    public static final int MAX_Q_LENGTH = 200;

    public StaffWorkshopFilter {
        if (
            status != null &&
            status != WorkshopStatus.PUBLISHED &&
            status != WorkshopStatus.COMPLETED
        ) {
            throw new BadRequestException(
                "The 'status' parameter must be 'PUBLISHED' or 'COMPLETED'."
            );
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException(
                "The 'from' timestamp must be before or equal to the 'to' timestamp."
            );
        }
        if (q != null && q.length() > MAX_Q_LENGTH) {
            throw new BadRequestException(
                "The 'q' parameter must not exceed " +
                    MAX_Q_LENGTH +
                    " characters."
            );
        }
    }

    /**
     * Constructs an empty filter with no criteria.
     */
    public static StaffWorkshopFilter empty() {
        return new StaffWorkshopFilter(null, null, null, null);
    }

    /**
     * Parses and validates raw string filter inputs from query parameters.
     *
     * @param statusStr raw status parameter
     * @param fromStr raw from timestamp parameter
     * @param toStr raw to timestamp parameter
     * @param qStr raw search keyword parameter
     * @return validated filter instance
     */
    public static StaffWorkshopFilter of(
        String statusStr,
        String fromStr,
        String toStr,
        String qStr
    ) {
        WorkshopStatus parsedStatus = parseStatus(statusStr);
        Instant parsedFrom = parseInstant(fromStr, "from");
        Instant parsedTo = parseInstant(toStr, "to");
        String parsedQ = parseQ(qStr);

        return new StaffWorkshopFilter(
            parsedStatus,
            parsedFrom,
            parsedTo,
            parsedQ
        );
    }

    /**
     * Checks if this filter contains no active criteria.
     */
    public boolean isEmpty() {
        return (
            status == null &&
            from == null &&
            to == null &&
            (q == null || q.isBlank())
        );
    }

    /**
     * Computes canonical filter fingerprint for keyset pagination cursors.
     */
    public String toFingerprint() {
        return FilterFingerprint.builder()
            .with("status", status)
            .with("from", from)
            .with("to", to)
            .with("q", q)
            .build();
    }

    private static WorkshopStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        if ("PUBLISHED".equals(statusStr)) {
            return WorkshopStatus.PUBLISHED;
        }
        if ("COMPLETED".equals(statusStr)) {
            return WorkshopStatus.COMPLETED;
        }
        throw new BadRequestException(
            "The 'status' parameter must be 'PUBLISHED' or 'COMPLETED'."
        );
    }

    private static Instant parseInstant(String instantStr, String paramName) {
        if (instantStr == null || instantStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(instantStr.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(
                "The '" +
                    paramName +
                    "' parameter must be a valid ISO-8601 UTC timestamp."
            );
        }
    }

    private static String parseQ(String qStr) {
        if (qStr == null) {
            return null;
        }
        String trimmed = qStr.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_Q_LENGTH) {
            throw new BadRequestException(
                "The 'q' parameter must not exceed " +
                    MAX_Q_LENGTH +
                    " characters."
            );
        }
        return trimmed;
    }
}
