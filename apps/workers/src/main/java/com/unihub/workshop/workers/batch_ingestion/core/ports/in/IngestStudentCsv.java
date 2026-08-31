package com.unihub.workshop.workers.batch_ingestion.core.ports.in;

import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;

public interface IngestStudentCsv {
    void ingest(SourceFile file);
}
