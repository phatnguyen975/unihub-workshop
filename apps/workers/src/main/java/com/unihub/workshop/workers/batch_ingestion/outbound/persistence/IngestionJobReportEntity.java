package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_job_reports")
@Getter
@Setter
public class IngestionJobReportEntity {

    @Id
    private UUID id;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "source_file_key")
    private String sourceFileKey;

    private String outcome;

    @Column(name = "processed_row_count")
    private int processedRowCount;

    @Column(name = "rejected_row_count")
    private int rejectedRowCount;

    @Column(name = "file_rejection_source_key")
    private String fileRejectionSourceKey;

    @Column(name = "reject_artifact_key")
    private String rejectArtifactKey;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
