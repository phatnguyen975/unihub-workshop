package com.unihub.workshop.search.application.ports.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.shared.exception.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchWorkshopsQueryTest {

    @Test
    @DisplayName(
        "of: creates valid SearchWorkshopsQuery with default sort and size"
    )
    void shouldCreateQueryWithDefaults() {
        SearchWorkshopsQuery query = SearchWorkshopsQuery.of(
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertThat(query.q()).isNull();
        assertThat(query.priceType()).isNull();
        assertThat(query.from()).isNull();
        assertThat(query.to()).isNull();
        assertThat(query.sort()).isEqualTo("startsAt,asc");
        assertThat(query.cursor()).isNull();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("of: parses valid ISO-8601 timestamps and checks from < to")
    void shouldParseTimestampsAndCheckOrder() {
        SearchWorkshopsQuery query = SearchWorkshopsQuery.of(
            " Spring Boot ",
            "FREE",
            "2026-11-01T00:00:00Z",
            "2026-11-30T23:59:59Z",
            "price,asc",
            "cursor-1",
            50
        );

        assertThat(query.q()).isEqualTo("Spring Boot");
        assertThat(query.priceType()).isEqualTo("FREE");
        assertThat(query.from()).isEqualTo(
            Instant.parse("2026-11-01T00:00:00Z")
        );
        assertThat(query.to()).isEqualTo(Instant.parse("2026-11-30T23:59:59Z"));
        assertThat(query.sort()).isEqualTo("price,asc");
        assertThat(query.cursor()).isEqualTo("cursor-1");
        assertThat(query.size()).isEqualTo(50);
    }

    @Test
    @DisplayName("of: rejects invalid 'from' timestamp format")
    void shouldRejectInvalidFromTimestamp() {
        assertThatThrownBy(() ->
            SearchWorkshopsQuery.of(
                null,
                null,
                "invalid-date",
                null,
                null,
                null,
                null
            )
        )
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getMessage()).isEqualTo(
                    "must be a valid ISO-8601 UTC timestamp"
                );
                assertThat(ve.getValidationErrors()).containsExactly(
                    "from: must be a valid ISO-8601 UTC timestamp"
                );
            });
    }

    @Test
    @DisplayName("of: rejects invalid 'to' timestamp format")
    void shouldRejectInvalidToTimestamp() {
        assertThatThrownBy(() ->
            SearchWorkshopsQuery.of(
                null,
                null,
                null,
                "invalid-date",
                null,
                null,
                null
            )
        )
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getMessage()).isEqualTo(
                    "must be a valid ISO-8601 UTC timestamp"
                );
                assertThat(ve.getValidationErrors()).containsExactly(
                    "to: must be a valid ISO-8601 UTC timestamp"
                );
            });
    }

    @Test
    @DisplayName("of: rejects when from >= to")
    void shouldRejectWhenFromAfterTo() {
        assertThatThrownBy(() ->
            SearchWorkshopsQuery.of(
                null,
                null,
                "2026-12-01T00:00:00Z",
                "2026-11-01T00:00:00Z",
                null,
                null,
                null
            )
        )
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getMessage()).isEqualTo(
                    "must be strictly earlier than to"
                );
                assertThat(ve.getValidationErrors()).containsExactly(
                    "from: must be strictly earlier than to"
                );
            });
    }

    @Test
    @DisplayName("of: rejects size greater than 100 or less than 1")
    void shouldRejectInvalidSize() {
        assertThatThrownBy(() ->
            SearchWorkshopsQuery.of(null, null, null, null, null, null, 101)
        )
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getMessage()).isEqualTo(
                    "must be between 1 and 100"
                );
                assertThat(ve.getValidationErrors()).containsExactly(
                    "size: must be between 1 and 100"
                );
            });

        assertThatThrownBy(() ->
            SearchWorkshopsQuery.of(null, null, null, null, null, null, 0)
        )
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getMessage()).isEqualTo(
                    "must be between 1 and 100"
                );
                assertThat(ve.getValidationErrors()).containsExactly(
                    "size: must be between 1 and 100"
                );
            });
    }

    @Test
    @DisplayName("of: rejects invalid priceType")
    void shouldRejectInvalidPriceType() {
        assertThatThrownBy(() ->
            SearchWorkshopsQuery.of(
                null,
                "INVALID",
                null,
                null,
                null,
                null,
                null
            )
        )
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getMessage()).isEqualTo(
                    "must be one of FREE, PAID"
                );
                assertThat(ve.getValidationErrors()).containsExactly(
                    "priceType: must be one of FREE, PAID"
                );
            });
    }

    @Test
    @DisplayName("of: rejects invalid sort")
    void shouldRejectInvalidSort() {
        assertThatThrownBy(() ->
            SearchWorkshopsQuery.of(
                null,
                null,
                null,
                null,
                "unknown,asc",
                null,
                null
            )
        )
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getMessage()).contains("must be one of");
                assertThat(ve.getValidationErrors()).allMatch(err ->
                    err.startsWith("sort: must be one of")
                );
            });
    }

    @Test
    @DisplayName("of: rejects q longer than 200 characters")
    void shouldRejectOverlongKeyword() {
        String longQ = "a".repeat(201);
        assertThatThrownBy(() ->
            SearchWorkshopsQuery.of(longQ, null, null, null, null, null, null)
        )
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getMessage()).isEqualTo(
                    "must not exceed 200 characters"
                );
                assertThat(ve.getValidationErrors()).containsExactly(
                    "q: must not exceed 200 characters"
                );
            });
    }
}
