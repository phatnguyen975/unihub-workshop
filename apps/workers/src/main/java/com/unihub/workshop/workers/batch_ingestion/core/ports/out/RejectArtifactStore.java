package com.unihub.workshop.workers.batch_ingestion.core.ports.out;

import com.unihub.workshop.workers.batch_ingestion.core.models.RejectedStudentRow;

public interface RejectArtifactStore {
    void append(long jobExecutionId, RejectedStudentRow row);

    String uploadAndCleanUp(long jobExecutionId, String organizationSlug, String sourceFileKey);
}
