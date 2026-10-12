package com.unihub.workshop.workers.ai_processing.outbound.persistence;

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
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "ai_summary_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class AiSummaryJobJpaEntity {

    @Id
    private UUID id;

    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;

    @Column(name = "brochure_id", nullable = false)
    private UUID brochureId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "result_text")
    private String resultText;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
}
