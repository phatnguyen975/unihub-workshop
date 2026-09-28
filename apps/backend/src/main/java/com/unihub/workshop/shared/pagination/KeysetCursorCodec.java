package com.unihub.workshop.shared.pagination;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.InternalServerException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes and decodes opaque Base64 JSON cursor strings for keyset pagination.
 *
 * <p>Enforces cursor validation and detects sorting parameter mismatches to prevent
 * corrupted pagination state across client navigation.
 */
public final class KeysetCursorCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private KeysetCursorCodec() {}

    /**
     * Encodes a {@link KeysetCursor} into a Base64 string.
     *
     * @param cursor the cursor object
     * @return the Base64 string, or null if cursor is null
     */
    public static String encode(KeysetCursor cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(cursor);
            return Base64.getEncoder().encodeToString(
                json.getBytes(StandardCharsets.UTF_8)
            );
        } catch (JsonProcessingException e) {
            throw new InternalServerException(
                "Failed to encode next cursor",
                e
            );
        }
    }

    /**
     * Decodes a Base64 cursor string and validates integrity and sort compatibility.
     *
     * @param cursorStr the encoded cursor string
     * @param expectedSortField the expected primary sort field of the current request, or null to skip check
     * @return the decoded {@link KeysetCursor}, or null if input is null or blank
     * @throws BadRequestException if the cursor is malformed, missing an ID, or issued for a different sort field
     */
    public static KeysetCursor decode(
        String cursorStr,
        String expectedSortField
    ) {
        if (cursorStr == null || cursorStr.isBlank()) {
            return null;
        }
        try {
            String trimmed = cursorStr.trim();
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(trimmed);
            } catch (IllegalArgumentException ex) {
                bytes = Base64.getUrlDecoder().decode(trimmed);
            }

            String json = new String(bytes, StandardCharsets.UTF_8);
            KeysetCursor cursor = OBJECT_MAPPER.readValue(
                json,
                KeysetCursor.class
            );

            if (cursor == null || cursor.id() == null) {
                throw new BadRequestException("Invalid cursor format");
            }

            if (
                expectedSortField != null &&
                cursor.sortField() != null &&
                !cursor.sortField().equalsIgnoreCase(expectedSortField)
            ) {
                throw new BadRequestException(
                    "Cursor was issued for sort='" +
                        cursor.sortField() +
                        "' but current request uses sort='" +
                        expectedSortField +
                        "'. Discard the cursor and start from page 1."
                );
            }

            return cursor;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Invalid cursor format");
        }
    }
}
