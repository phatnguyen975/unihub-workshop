package com.unihub.workshop.workshops.application.ports.out;

import com.unihub.workshop.workshops.domain.model.AiSummaryJob;
import com.unihub.workshop.workshops.domain.model.AiSummaryJobStatus;
import java.util.Optional;
import java.util.UUID;

public interface AiSummaryJobRepository {
    AiSummaryJob save(AiSummaryJob job);

    boolean existsByWorkshopIdAndStatus(
        UUID workshopId,
        AiSummaryJobStatus status
    );

    Optional<AiSummaryJob> findLatestByWorkshopId(UUID workshopId);
}
