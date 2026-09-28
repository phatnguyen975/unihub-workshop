package com.unihub.workshop.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.shared.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeysetCursorCodecTest {

    @Test
    @DisplayName(
        "encode and decode roundtrip with primary and secondary values"
    )
    void shouldRoundtripWithPrimaryAndSecondaryValues() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        KeysetCursor cursor = KeysetCursor.of(
            id,
            "startsAt",
            now.toString(),
            now.minusSeconds(10).toString()
        );

        String encoded = KeysetCursorCodec.encode(cursor);
        assertThat(encoded).isNotNull();

        KeysetCursor decoded = KeysetCursorCodec.decode(encoded, "startsAt");
        assertThat(decoded).isNotNull();
        assertThat(decoded.id()).isEqualTo(id);
        assertThat(decoded.sortField()).isEqualTo("startsAt");
        assertThat(decoded.asInstant()).isEqualTo(now);
        assertThat(decoded.asSecondaryInstant()).isEqualTo(
            now.minusSeconds(10)
        );
    }

    @Test
    @DisplayName("decode returns null for null or blank input")
    void shouldReturnNullForNullOrBlank() {
        assertThat(KeysetCursorCodec.decode(null, "createdAt")).isNull();
        assertThat(KeysetCursorCodec.decode("", "createdAt")).isNull();
        assertThat(KeysetCursorCodec.decode("   ", "createdAt")).isNull();
    }

    @Test
    @DisplayName(
        "decode detects sort field mismatch and throws BadRequestException"
    )
    void shouldThrowBadRequestWhenSortFieldMismatches() {
        UUID id = UUID.randomUUID();
        KeysetCursor cursor = KeysetCursor.of(id, "price", "100.00");
        String encoded = KeysetCursorCodec.encode(cursor);

        assertThatThrownBy(() -> KeysetCursorCodec.decode(encoded, "createdAt"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "Cursor was issued for sort='price' but current request uses sort='createdAt'"
            );
    }

    @Test
    @DisplayName(
        "decode throws BadRequestException on invalid base64 or corrupt json"
    )
    void shouldThrowBadRequestOnMalformedInput() {
        assertThatThrownBy(() ->
            KeysetCursorCodec.decode("not-base64!@#$", "createdAt")
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Invalid cursor format");

        String invalidJsonBase64 = Base64.getEncoder().encodeToString(
            "{\"invalid\":\"field\"}".getBytes()
        );
        assertThatThrownBy(() ->
            KeysetCursorCodec.decode(invalidJsonBase64, "createdAt")
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Invalid cursor format");
    }

    @Test
    @DisplayName(
        "decode parses standard JSON with primaryValue, sortField, and id"
    )
    void shouldDecodeStandardJsonPayload() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        String json = String.format(
            "{\"id\":\"%s\",\"sortField\":\"createdAt\",\"primaryValue\":\"%s\"}",
            id,
            now
        );
        String encoded = Base64.getEncoder().encodeToString(json.getBytes());

        KeysetCursor decoded = KeysetCursorCodec.decode(encoded, "createdAt");
        assertThat(decoded).isNotNull();
        assertThat(decoded.id()).isEqualTo(id);
        assertThat(decoded.asInstant()).isEqualTo(now);
    }

    @Test
    @DisplayName(
        "helper methods correctly parse types from generic primary and secondary values"
    )
    void shouldParseTypesCorrectly() {
        UUID id = UUID.randomUUID();
        KeysetCursor priceCursor = new KeysetCursor(
            id,
            "price",
            "49.99",
            "2026-09-04T10:00:00Z"
        );
        assertThat(priceCursor.asBigDecimal()).isEqualByComparingTo(
            new BigDecimal("49.99")
        );
        assertThat(priceCursor.asDouble()).isEqualTo(49.99);
        assertThat(priceCursor.asSecondaryInstant()).isEqualTo(
            Instant.parse("2026-09-04T10:00:00Z")
        );

        KeysetCursor intCursor = new KeysetCursor(id, "capacity", "50", null);
        assertThat(intCursor.asInteger()).isEqualTo(50);
        assertThat(intCursor.asLong()).isEqualTo(50L);
    }
}
