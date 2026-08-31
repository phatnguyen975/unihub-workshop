package com.unihub.workshop.workers.batch_ingestion.core.pipeline;

import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;

public interface StreamingStudentCsvReader extends AutoCloseable {
    void open(String sourceFileKey, java.util.UUID organizationId);

    StudentDirectoryRow readRow();
}
