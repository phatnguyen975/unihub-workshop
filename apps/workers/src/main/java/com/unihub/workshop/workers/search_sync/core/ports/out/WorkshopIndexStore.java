package com.unihub.workshop.workers.search_sync.core.ports.out;

import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port for persisting and deleting workshop search documents in Elasticsearch.
 */
public interface WorkshopIndexStore {
    void upsert(WorkshopDocument document);

    void updateSchedule(
        UUID workshopId,
        Instant startsAt,
        Instant endsAt,
        String timezone
    );

    void delete(UUID workshopId);
}
