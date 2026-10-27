package com.unihub.workshop.shared.pagination;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.shared.exception.BadRequestException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

/**
 * Universal cursor payload for keyset (seek-based) pagination across UniHub.
 *
 * <p>Shared contract between JPA persistence, Elasticsearch read models, and REST endpoints.
 * Operates purely on generic primary and secondary sort value representations without
 * hardcoding domain-specific fields.
 *
 * @param id unique row tie-breaker identifier (mandatory)
 * @param sortField the mandatory name of the sorted field (e.g. "createdAt", "startsAt", "price", "capacity", "completedAt", "rating")
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
     *
     * @throws BadRequestException when the value is not a valid timestamp
     */
    public Instant asInstant() {
        return parse(primaryValue, Instant::parse);
    }

    /**
     * Resolves the secondary sort value as an {@link Instant} (typically createdAt).
     *
     * @throws BadRequestException when the value is not a valid timestamp
     */
    public Instant asSecondaryInstant() {
        return parse(secondaryValue, Instant::parse);
    }

    /**
     * Resolves the primary sort value as a {@link BigDecimal}.
     *
     * @throws BadRequestException when the value is not a valid decimal number
     */
    public BigDecimal asBigDecimal() {
        return parse(primaryValue, BigDecimal::new);
    }

    /**
     * Resolves the primary sort value as an {@link Integer}. Integral and decimal
     * representations are accepted; the decimal form is truncated to its integer part.
     *
     * @throws BadRequestException when the value is not a valid number
     */
    public Integer asInteger() {
        if (primaryValue == null || primaryValue.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(primaryValue);
        } catch (NumberFormatException e) {
            return parse(
                primaryValue,
                value -> (int) Double.parseDouble(value)
            );
        }
    }

    /**
     * Resolves the primary sort value as a {@link Long}.
     *
     * @throws BadRequestException when the value is not a valid long
     */
    public Long asLong() {
        return parse(primaryValue, Long::parseLong);
    }

    /**
     * Resolves the primary sort value as a {@link Double}.
     *
     * @throws BadRequestException when the value is not a valid number
     */
    public Double asDouble() {
        return parse(primaryValue, Double::parseDouble);
    }

    private static <T> T parse(String value, Function<String, T> parser) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (RuntimeException e) {
            throw new BadRequestException("Invalid cursor format");
        }
    }
}
