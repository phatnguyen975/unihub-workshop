package com.unihub.workshop.attendances.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceSummaryTest {

    private final UUID workshopId = UUID.randomUUID();

    @Test
    void percentageUsesConfirmedCountAndHalfUpRoundingWithoutCap() {
        assertThat(
            AttendanceSummary.of(
                workshopId,
                100,
                80,
                50,
                List.of()
            ).checkInPercentage()
        ).isEqualTo(new BigDecimal("62.50"));
        assertThat(
            AttendanceSummary.of(
                workshopId,
                100,
                32,
                1,
                List.of()
            ).checkInPercentage()
        ).isEqualTo(new BigDecimal("3.13"));
        assertThat(
            AttendanceSummary.of(
                workshopId,
                100,
                3,
                2,
                List.of()
            ).checkInPercentage()
        ).isEqualTo(new BigDecimal("66.67"));
        assertThat(
            AttendanceSummary.of(
                workshopId,
                100,
                2,
                3,
                List.of()
            ).checkInPercentage()
        ).isEqualTo(new BigDecimal("150.00"));
        assertThat(
            AttendanceSummary.of(
                workshopId,
                100,
                0,
                1,
                List.of()
            ).checkInPercentage()
        ).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void previewIsDefensivelyCopied() {
        Attendance scan = Attendance.builder().id(UUID.randomUUID()).build();
        List<Attendance> input = new ArrayList<>(List.of(scan));
        AttendanceSummary summary = AttendanceSummary.of(
            workshopId,
            0,
            1,
            1,
            input
        );
        input.clear();
        assertThat(summary.recentScans()).containsExactly(scan);
        assertThatThrownBy(() -> summary.recentScans().clear()).isInstanceOf(
            UnsupportedOperationException.class
        );
    }
}
