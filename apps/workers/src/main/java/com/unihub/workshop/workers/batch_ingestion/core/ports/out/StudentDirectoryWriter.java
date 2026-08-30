package com.unihub.workshop.workers.batch_ingestion.core.ports.out;

import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;
import java.util.List;

public interface StudentDirectoryWriter {
    void bulkUpsert(List<StudentDirectoryRow> rows, String sourceFileKey);
}
