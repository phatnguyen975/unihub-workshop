package com.unihub.workshop.workers.search_sync.core.ports.in;

import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port driving workshop Elasticsearch synchronization and Redis maintenance.
 */
public interface SyncWorkshopIndex {
    void handleWorkshopPublished(WorkshopDocument document);

    void handleWorkshopUpdated(WorkshopDocument document);

    void handleWorkshopRescheduled(
        UUID workshopId,
        Instant startsAt,
        Instant endsAt,
        String timezone
    );

    void handleWorkshopCompleted(UUID workshopId);

    void handleWorkshopCanceled(UUID workshopId);
}
