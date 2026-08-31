package com.unihub.workshop.workers.batch_ingestion.core.models;

public record IngestionMetrics(int processedRowCount, int rejectCount) {
}
