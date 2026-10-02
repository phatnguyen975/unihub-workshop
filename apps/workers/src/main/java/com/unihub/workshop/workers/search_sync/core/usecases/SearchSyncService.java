package com.unihub.workshop.workers.search_sync.core.usecases;

import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import com.unihub.workshop.workers.search_sync.core.ports.in.SyncWorkshopIndex;
import com.unihub.workshop.workers.search_sync.core.ports.out.SearchCacheInvalidator;
import com.unihub.workshop.workers.search_sync.core.ports.out.WorkshopCapacityStore;
import com.unihub.workshop.workers.search_sync.core.ports.out.WorkshopIndexStore;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchSyncService implements SyncWorkshopIndex {

    private final WorkshopIndexStore workshopIndexStore;
    private final SearchCacheInvalidator searchCacheInvalidator;
    private final WorkshopCapacityStore workshopCapacityStore;

    @Override
    public void handleWorkshopPublished(WorkshopDocument document) {
        workshopIndexStore.upsert(document);
        int capacity =
            document != null && document.capacity() != null
                ? document.capacity()
                : 0;
        if (document != null && document.id() != null) {
            workshopCapacityStore.set(document.id(), capacity);
        }
    }

    @Override
    public void handleWorkshopUpdated(WorkshopDocument document) {
        workshopIndexStore.upsert(document);
        searchCacheInvalidator.invalidateAll();
    }

    @Override
    public void handleWorkshopRescheduled(
        UUID workshopId,
        Instant startsAt,
        Instant endsAt,
        String timezone
    ) {
        workshopIndexStore.updateSchedule(
            workshopId,
            startsAt,
            endsAt,
            timezone
        );
        searchCacheInvalidator.invalidateAll();
    }

    @Override
    public void handleWorkshopCompleted(UUID workshopId) {
        workshopIndexStore.delete(workshopId);
        searchCacheInvalidator.invalidateAll();
    }

    @Override
    public void handleWorkshopCanceled(UUID workshopId) {
        workshopIndexStore.delete(workshopId);
        searchCacheInvalidator.invalidateAll();
    }
}
