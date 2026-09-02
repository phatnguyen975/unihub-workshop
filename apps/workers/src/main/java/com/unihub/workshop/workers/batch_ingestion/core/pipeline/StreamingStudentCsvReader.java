package com.unihub.workshop.workers.batch_ingestion.core.pipeline;

import java.util.UUID;

import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;

public interface StreamingStudentCsvReader extends AutoCloseable {
    void open(String sourceFileKey, UUID organizationId);

    StudentDirectoryRow readRow();
}
