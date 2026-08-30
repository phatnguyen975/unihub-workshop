package com.unihub.workshop.workers.batch_ingestion.core.ports.out;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;

public interface IngestionJobReportWriter {
    void save(IngestionJobReport report);
}
