package com.unihub.workshop.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FilterFingerprintTest {

    @Test
    @DisplayName("encodes a single filter as name=value")
    void shouldEncodeSingleFilter() {
        String fingerprint = FilterFingerprint.builder()
            .with("isRead", true)
            .build();

        assertThat(fingerprint).isEqualTo("isRead=true");
    }

    @Test
    @DisplayName("encodes an absent filter with the wildcard marker")
    void shouldEncodeAbsentFilterWithWildcard() {
        String fingerprint = FilterFingerprint.builder()
            .with("status", null)
            .build();

        assertThat(fingerprint).isEqualTo("status=*");
    }

    @Test
    @DisplayName("joins multiple filters in declaration order")
    void shouldJoinFiltersInDeclarationOrder() {
        String fingerprint = FilterFingerprint.builder()
            .with("role", "STUDENT")
            .with("isActive", true)
            .with("q", "alice")
            .build();

        assertThat(fingerprint).isEqualTo("role=STUDENT&isActive=true&q=alice");
    }

    @Test
    @DisplayName("uses the enum constant name instead of toString")
    void shouldUseEnumConstantName() {
        String fingerprint = FilterFingerprint.builder()
            .with("status", SampleStatus.PENDING)
            .build();

        assertThat(fingerprint).isEqualTo("status=PENDING");
    }

    @Test
    @DisplayName("serializes timestamps in ISO-8601 form")
    void shouldSerializeInstantInIsoForm() {
        String fingerprint = FilterFingerprint.builder()
            .with("from", Instant.parse("2026-10-21T05:12:00Z"))
            .build();

        assertThat(fingerprint).isEqualTo("from=2026-10-21T05%3A12%3A00Z");
    }

    @Test
    @DisplayName(
        "percent-encodes separators so a value cannot forge another fingerprint"
    )
    void shouldPercentEncodeSeparatorsInValues() {
        String suspicious = FilterFingerprint.builder()
            .with("q", "ai&status=PAID")
            .build();
        String honest = FilterFingerprint.builder()
            .with("q", "ai")
            .with("status", "PAID")
            .build();

        assertThat(suspicious).isEqualTo("q=ai%26status%3DPAID");
        assertThat(suspicious).isNotEqualTo(honest);
    }

    private enum SampleStatus {
        PENDING,
    }
}
