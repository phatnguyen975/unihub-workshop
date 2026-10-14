package com.unihub.workshop.shared.idempotency;

import com.unihub.workshop.shared.exception.ValidationException;
import java.util.UUID;

/**
 * Validates and normalizes HTTP Idempotency-Key headers across mutating endpoints.
 * Enforces presence, non-blank, max length 64 characters, whitespace trimming, and RFC 4122 UUID format.
 */
public final class IdempotencyKeyValidator {

    public static final String HEADER_NAME = "Idempotency-Key";
    public static final int MAX_LENGTH = 64;

    private IdempotencyKeyValidator() {}

    /**
     * Validates and normalizes the incoming Idempotency-Key header.
     *
     * @param header raw header string (can be null or contain whitespace)
     * @return trimmed, validated UUIDv4 string
     * @throws ValidationException if header is null, blank, exceeds max length, or is not a valid UUID
     */
    public static String validateAndNormalize(String header) {
        if (header == null || header.isBlank()) {
            throw new ValidationException(
                HEADER_NAME,
                "Idempotency-Key header is required"
            );
        }
        String trimmed = header.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new ValidationException(
                HEADER_NAME,
                "Idempotency-Key header must not exceed " +
                    MAX_LENGTH +
                    " characters"
            );
        }
        try {
            UUID.fromString(trimmed);
            return trimmed;
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                HEADER_NAME,
                "Idempotency-Key header must be a valid UUIDv4"
            );
        }
    }
}
