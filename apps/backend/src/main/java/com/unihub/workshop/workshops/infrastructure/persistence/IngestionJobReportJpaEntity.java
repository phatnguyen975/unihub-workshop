package com.unihub.workshop.workshops.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ingestion_job_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IngestionJobReportJpaEntity {

    @Id
    private UUID id;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "source_file_key", nullable = false)
    private String sourceFileKey;

    @Column(name = "outcome", nullable = false)
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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
