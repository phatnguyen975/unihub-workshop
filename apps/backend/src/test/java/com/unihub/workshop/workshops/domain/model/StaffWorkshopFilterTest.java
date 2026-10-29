package com.unihub.workshop.workshops.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.shared.exception.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaffWorkshopFilterTest {

    @Test
    @DisplayName("Should create valid filter when all parameters are valid")
    void shouldCreateValidFilter_whenAllParametersValid() {
        StaffWorkshopFilter filter = StaffWorkshopFilter.of(
            "PUBLISHED",
            "2026-11-12T00:00:00Z",
            "2026-11-18T00:00:00Z",
            "  python  "
        );

        assertThat(filter.status()).isEqualTo(WorkshopStatus.PUBLISHED);
        assertThat(filter.from()).isEqualTo(
            Instant.parse("2026-11-12T00:00:00Z")
        );
        assertThat(filter.to()).isEqualTo(
            Instant.parse("2026-11-18T00:00:00Z")
        );
        assertThat(filter.q()).isEqualTo("python");
        assertThat(filter.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Should accept COMPLETED status filter")
    void shouldAcceptCompletedStatus() {
        StaffWorkshopFilter filter = StaffWorkshopFilter.of(
            "COMPLETED",
            null,
            null,
            null
        );

        assertThat(filter.status()).isEqualTo(WorkshopStatus.COMPLETED);
        assertThat(filter.from()).isNull();
        assertThat(filter.to()).isNull();
        assertThat(filter.q()).isNull();
    }

    @Test
    @DisplayName("Should reject CANCELED status filter")
    void shouldRejectCanceledStatus() {
        assertThatThrownBy(() ->
            StaffWorkshopFilter.of("CANCELED", null, null, null)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'status' parameter must be 'PUBLISHED' or 'COMPLETED'."
            );
    }

    @Test
    @DisplayName("Should reject DRAFT and unknown status filters")
    void shouldRejectDraftAndUnknownStatus() {
        assertThatThrownBy(() ->
            StaffWorkshopFilter.of("DRAFT", null, null, null)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'status' parameter must be 'PUBLISHED' or 'COMPLETED'."
            );

        assertThatThrownBy(() ->
            StaffWorkshopFilter.of("UNKNOWN", null, null, null)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'status' parameter must be 'PUBLISHED' or 'COMPLETED'."
            );

        assertThatThrownBy(() ->
            StaffWorkshopFilter.of("published", null, null, null)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'status' parameter must be 'PUBLISHED' or 'COMPLETED'."
            );
    }

    @Test
    @DisplayName("Should reject malformed from timestamp")
    void shouldRejectMalformedFromTimestamp() {
        assertThatThrownBy(() ->
            StaffWorkshopFilter.of(null, "invalid-timestamp", null, null)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'from' parameter must be a valid ISO-8601 UTC timestamp."
            );
    }

    @Test
    @DisplayName("Should reject malformed to timestamp")
    void shouldRejectMalformedToTimestamp() {
        assertThatThrownBy(() ->
            StaffWorkshopFilter.of(null, null, "not-a-date", null)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'to' parameter must be a valid ISO-8601 UTC timestamp."
            );
    }

    @Test
    @DisplayName("Should reject when from is after to")
    void shouldRejectWhenFromIsAfterTo() {
        assertThatThrownBy(() ->
            StaffWorkshopFilter.of(
                null,
                "2026-11-20T00:00:00Z",
                "2026-11-10T00:00:00Z",
                null
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'from' timestamp must be before or equal to the 'to' timestamp."
            );
    }

    @Test
    @DisplayName("Should accept when from equals to")
    void shouldAcceptWhenFromEqualsTo() {
        StaffWorkshopFilter filter = StaffWorkshopFilter.of(
            null,
            "2026-11-15T00:00:00Z",
            "2026-11-15T00:00:00Z",
            null
        );

        assertThat(filter.from()).isEqualTo(filter.to());
    }

    @Test
    @DisplayName("Should treat empty or blank q as null")
    void shouldTreatEmptyOrBlankQAsNull() {
        StaffWorkshopFilter filter = StaffWorkshopFilter.of(
            null,
            null,
            null,
            "   "
        );
        assertThat(filter.q()).isNull();
        assertThat(filter.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Should reject q exceeding 200 characters")
    void shouldRejectQExceeding200Characters() {
        String longQ = "a".repeat(201);
        assertThatThrownBy(() ->
            StaffWorkshopFilter.of(null, null, null, longQ)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'q' parameter must not exceed 200 characters."
            );
    }

    @Test
    @DisplayName("Should generate canonical filter fingerprint")
    void shouldGenerateCanonicalFilterFingerprint() {
        StaffWorkshopFilter emptyFilter = StaffWorkshopFilter.empty();
        assertThat(emptyFilter.toFingerprint()).isEqualTo(
            "status=*&from=*&to=*&q=*"
        );

        StaffWorkshopFilter filtered = StaffWorkshopFilter.of(
            "PUBLISHED",
            "2026-11-12T00:00:00Z",
            null,
            "python"
        );
        assertThat(filtered.toFingerprint()).isEqualTo(
            "status=PUBLISHED&from=2026-11-12T00%3A00%3A00Z&to=*&q=python"
        );
    }
}
