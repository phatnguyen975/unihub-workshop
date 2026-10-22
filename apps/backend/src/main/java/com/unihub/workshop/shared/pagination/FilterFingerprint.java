package com.unihub.workshop.shared.pagination;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds the canonical fingerprint that binds a keyset cursor to the filter set
 * which produced it.
 *
 * <p>Callers declare every filter that narrows the result set, in a stable
 * order, including filters that are absent (encoded as {@code *}). Encoding an
 * absent filter matters: it keeps "no filter" and "filter X" as two different
 * fingerprints, so a client that adds or drops a filter while keeping a cursor
 * is rejected instead of silently receiving a misaligned page.
 *
 * <p>Sorting, page size, and the cursor itself are never part of the
 * fingerprint, because {@link KeysetCursorCodec} validates the sort field
 * independently.
 *
 * <p>Values are percent-encoded so that a filter value containing
 * {@code =} or {@code &} cannot forge a different fingerprint.
 */
public final class FilterFingerprint {

    private static final char SEPARATOR = '&';
    private static final char ASSIGNMENT = '=';
    private static final String ABSENT_VALUE = "*";

    private FilterFingerprint() {}

    /**
     * Starts a fingerprint with no filter declared.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Accumulates filter entries in declaration order.
     */
    public static final class Builder {

        private final StringBuilder fingerprint = new StringBuilder();

        private Builder() {}

        /**
         * Declares one result-set filter.
         *
         * @param name canonical filter name
         * @param value filter value; {@code null} marks the filter as absent
         * @return this builder
         */
        public Builder with(String name, Object value) {
            if (!fingerprint.isEmpty()) {
                fingerprint.append(SEPARATOR);
            }
            fingerprint.append(name).append(ASSIGNMENT).append(encode(value));
            return this;
        }

        /**
         * Returns the canonical fingerprint of every declared filter.
         */
        public String build() {
            return fingerprint.toString();
        }

        private String encode(Object value) {
            if (value == null) {
                return ABSENT_VALUE;
            }
            String rawValue =
                value instanceof Enum<?> enumValue
                    ? enumValue.name()
                    : String.valueOf(value);
            return URLEncoder.encode(rawValue, StandardCharsets.UTF_8);
        }
    }
}
