package com.unihub.workshop.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.shared.exception.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeyValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    @DisplayName(
        "Throws ValidationException when Idempotency-Key is null or blank"
    )
    void validateAndNormalize_NullOrBlank_ThrowsValidationException(
        String header
    ) {
        assertThatThrownBy(() ->
            IdempotencyKeyValidator.validateAndNormalize(header)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Idempotency-Key header is required");
    }

    @Test
    @DisplayName(
        "Throws ValidationException when Idempotency-Key exceeds 64 characters"
    )
    void validateAndNormalize_ExceedsMaxLength_ThrowsValidationException() {
        String longHeader = "a".repeat(65);
        assertThatThrownBy(() ->
            IdempotencyKeyValidator.validateAndNormalize(longHeader)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must not exceed 64 characters");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "not-a-uuid",
            "12345",
            "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz",
        }
    )
    @DisplayName(
        "Throws ValidationException when Idempotency-Key is not a valid UUIDv4"
    )
    void validateAndNormalize_InvalidUuid_ThrowsValidationException(
        String invalidUuid
    ) {
        assertThatThrownBy(() ->
            IdempotencyKeyValidator.validateAndNormalize(invalidUuid)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must be a valid UUIDv4");
    }

    @Test
    @DisplayName("Returns normalized UUID string when header is valid")
    void validateAndNormalize_ValidUuid_ReturnsNormalizedString() {
        UUID uuid = UUID.randomUUID();
        String result = IdempotencyKeyValidator.validateAndNormalize(
            uuid.toString()
        );
        assertThat(result).isEqualTo(uuid.toString());
    }

    @Test
    @DisplayName("Trims whitespace and returns normalized UUID string")
    void validateAndNormalize_UuidWithWhitespace_TrimsAndReturnsString() {
        UUID uuid = UUID.randomUUID();
        String padded = "   " + uuid + " \t ";
        String result = IdempotencyKeyValidator.validateAndNormalize(padded);
        assertThat(result).isEqualTo(uuid.toString());
    }
}
