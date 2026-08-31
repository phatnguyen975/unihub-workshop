package com.unihub.workshop.workers.batch_ingestion.core.ports.out;

import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import java.util.List;

public interface IngestionObjectStore {
    List<SourceFile> listInboxFiles();

    boolean hasValidHeader(String fileKey);

    String moveToRejected(String fileKey, String organizationSlug);

    String moveToArchive(String fileKey, String organizationSlug);
}
