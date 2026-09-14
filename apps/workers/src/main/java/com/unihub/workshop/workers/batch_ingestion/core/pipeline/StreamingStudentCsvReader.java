package com.unihub.workshop.workers.batch_ingestion.core.pipeline;

import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;
import java.util.UUID;

public interface StreamingStudentCsvReader extends AutoCloseable {
    void open(String sourceFileKey, UUID organizationId);

    StudentDirectoryRow readRow();
}
