package com.unihub.workshop.shared.pagination;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Universal cursor payload for keyset (seek-based) pagination across UniHub.
 *
 * <p>Shared contract between JPA persistence, Elasticsearch read models, and REST endpoints.
 * Operates purely on generic primary and secondary sort value representations without
 * hardcoding domain-specific fields.
 *
 * @param id unique row tie-breaker identifier (mandatory)
 * @param sortField the name of the sorted field (e.g. "createdAt", "startsAt", "price", "capacity", "completedAt", "rating")
 * @param primaryValue string representation of the primary sort value, or null if value is null
 * @param secondaryValue string representation of the secondary sort value (e.g. createdAt ISO-8601), or null
 * @param filterFingerprint server-derived canonical string over the filters that produced the cursor, or null when the collection is unfiltered or the caller does not bind filters
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeysetCursor(
    UUID id,
    String sortField,
    String primaryValue,
    String secondaryValue,
    String filterFingerprint
) implements Serializable {
    /**
     * Resolves the primary sort value as an {@link Instant}.
     */
    public Instant asInstant() {
        if (primaryValue != null && !primaryValue.isBlank()) {
            return Instant.parse(primaryValue);
        }
        return null;
    }

    /**
     * Resolves the secondary sort value as an {@link Instant} (typically createdAt).
     */
    public Instant asSecondaryInstant() {
        if (secondaryValue != null && !secondaryValue.isBlank()) {
            return Instant.parse(secondaryValue);
        }
        return null;
    }

    /**
     * Resolves the primary sort value as a {@link BigDecimal}.
     */
    public BigDecimal asBigDecimal() {
        if (primaryValue != null && !primaryValue.isBlank()) {
            return new BigDecimal(primaryValue);
        }
        return null;
    }

    /**
     * Resolves the primary sort value as an {@link Integer}.
     */
    public Integer asInteger() {
        if (primaryValue != null && !primaryValue.isBlank()) {
            try {
                return Integer.parseInt(primaryValue);
            } catch (NumberFormatException e) {
                return (int) Double.parseDouble(primaryValue);
            }
        }
        return null;
    }

    /**
     * Resolves the primary sort value as a {@link Long}.
     */
    public Long asLong() {
        if (primaryValue != null && !primaryValue.isBlank()) {
            return Long.parseLong(primaryValue);
        }
        return null;
    }

    /**
     * Resolves the primary sort value as a {@link Double}.
     */
    public Double asDouble() {
        if (primaryValue != null && !primaryValue.isBlank()) {
            return Double.parseDouble(primaryValue);
        }
        return null;
    }
}
