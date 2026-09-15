package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ingestion_job_reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionJobReportEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_execution_id", unique = true)
    private Long jobExecutionId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "source_file_key", nullable = false)
    private String sourceFileKey;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "processed_row_count", nullable = false)
    private int processedRowCount;

    @Column(name = "rejected_row_count", nullable = false)
    private int rejectedRowCount;

    @Column(name = "file_rejection_source_key")
    private String fileRejectionSourceKey;

    @Column(name = "reject_artifact_key")
    private String rejectArtifactKey;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
