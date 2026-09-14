package com.unihub.workshop.workshops.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionJobReportTest {

    @Test
    void isArtifactAvailable_whenFileRejectedAndKeyPresent_returnsTrue() {
        IngestionJobReport report = new IngestionJobReport(
            UUID.randomUUID(),
            1L,
            UUID.randomUUID(),
            "inbox/file.csv",
            IngestionJobOutcome.FILE_REJECTED,
            0,
            0,
            "rejected/file.csv",
            null,
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
        assertTrue(report.isArtifactAvailable());
    }

    @Test
    void isArtifactAvailable_whenFileRejectedAndKeyNull_returnsFalse() {
        IngestionJobReport report = new IngestionJobReport(
            UUID.randomUUID(),
            1L,
            UUID.randomUUID(),
            "inbox/file.csv",
            IngestionJobOutcome.FILE_REJECTED,
            0,
            0,
            null,
            null,
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
        assertFalse(report.isArtifactAvailable());
    }

    @Test
    void isArtifactAvailable_whenCompletedAndRejectedRowsPresentAndKeyPresent_returnsTrue() {
        IngestionJobReport report = new IngestionJobReport(
            UUID.randomUUID(),
            1L,
            UUID.randomUUID(),
            "inbox/file.csv",
            IngestionJobOutcome.COMPLETED,
            100,
            5,
            null,
            "rejected/report.csv",
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
        assertTrue(report.isArtifactAvailable());
    }

    @Test
    void isArtifactAvailable_whenCompletedAndNoRejectedRows_returnsFalse() {
        IngestionJobReport report = new IngestionJobReport(
            UUID.randomUUID(),
            1L,
            UUID.randomUUID(),
            "inbox/file.csv",
            IngestionJobOutcome.COMPLETED,
            100,
            0,
            null,
            "rejected/report.csv",
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
        assertFalse(report.isArtifactAvailable());
    }

    @Test
    void isArtifactAvailable_whenCompletedAndKeyNull_returnsFalse() {
        IngestionJobReport report = new IngestionJobReport(
            UUID.randomUUID(),
            1L,
            UUID.randomUUID(),
            "inbox/file.csv",
            IngestionJobOutcome.COMPLETED,
            100,
            5,
            null,
            null,
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
        assertFalse(report.isArtifactAvailable());
    }
}
