package com.unihub.workshop.workshops.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IngestionJobReportTest {

    @Test
    void isArtifactAvailable_whenFileRejectedAndKeyPresent_returnsTrue() {
        IngestionJobReport report = IngestionJobReport.builder()
            .outcome(IngestionJobOutcome.FILE_REJECTED)
            .fileRejectionSourceKey("rejected/file.csv")
            .build();
        assertTrue(report.isArtifactAvailable());
    }

    @Test
    void isArtifactAvailable_whenFileRejectedAndKeyNull_returnsFalse() {
        IngestionJobReport report = IngestionJobReport.builder()
            .outcome(IngestionJobOutcome.FILE_REJECTED)
            .fileRejectionSourceKey(null)
            .build();
        assertFalse(report.isArtifactAvailable());
    }

    @Test
    void isArtifactAvailable_whenCompletedAndRejectedRowsPresentAndKeyPresent_returnsTrue() {
        IngestionJobReport report = IngestionJobReport.builder()
            .outcome(IngestionJobOutcome.COMPLETED)
            .rejectedRowCount(5)
            .rejectArtifactKey("rejected/report.csv")
            .build();
        assertTrue(report.isArtifactAvailable());
    }

    @Test
    void isArtifactAvailable_whenCompletedAndNoRejectedRows_returnsFalse() {
        IngestionJobReport report = IngestionJobReport.builder()
            .outcome(IngestionJobOutcome.COMPLETED)
            .rejectedRowCount(0)
            .rejectArtifactKey("rejected/report.csv")
            .build();
        assertFalse(report.isArtifactAvailable());
    }

    @Test
    void isArtifactAvailable_whenCompletedAndKeyNull_returnsFalse() {
        IngestionJobReport report = IngestionJobReport.builder()
            .outcome(IngestionJobOutcome.COMPLETED)
            .rejectedRowCount(5)
            .rejectArtifactKey(null)
            .build();
        assertFalse(report.isArtifactAvailable());
    }
}
