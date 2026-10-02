package com.unihub.workshop.workers.search_sync.core.usecases;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import com.unihub.workshop.workers.search_sync.core.ports.out.SearchCacheInvalidator;
import com.unihub.workshop.workers.search_sync.core.ports.out.WorkshopCapacityStore;
import com.unihub.workshop.workers.search_sync.core.ports.out.WorkshopIndexStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchSyncServiceTest {

    private WorkshopIndexStore workshopIndexStore;
    private SearchCacheInvalidator searchCacheInvalidator;
    private WorkshopCapacityStore workshopCapacityStore;
    private SearchSyncService searchSyncService;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopIndexStore = mock(WorkshopIndexStore.class);
        searchCacheInvalidator = mock(SearchCacheInvalidator.class);
        workshopCapacityStore = mock(WorkshopCapacityStore.class);
        searchSyncService = new SearchSyncService(
            workshopIndexStore,
            searchCacheInvalidator,
            workshopCapacityStore
        );
    }

    private WorkshopDocument createSampleDocument(UUID id, int capacity) {
        Instant now = Instant.now();
        return new WorkshopDocument(
            id,
            ORG_ID,
            "Hands-on AI Engineering",
            "hands-on-ai-engineering",
            "Comprehensive workshop on AI systems",
            "https://cdn.unihub.com/workshops/ai.png",
            "Hall B",
            "Asia/Ho_Chi_Minh",
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            capacity,
            BigDecimal.ZERO,
            "USD",
            "PUBLISHED",
            now,
            List.of(
                new WorkshopDocument.SpeakerDocument(
                    UUID.randomUUID(),
                    "Ada Lovelace",
                    "Pioneer programmer",
                    "https://cdn.unihub.com/speakers/ada.png",
                    "https://linkedin.com/in/ada",
                    "ada@lovelace.org",
                    0
                )
            )
        );
    }

    @Test
    @DisplayName(
        "handleWorkshopPublished: upserts ES document and seeds Redis capacity counter"
    )
    void shouldUpsertDocumentAndSeedCapacityOnPublished() {
        WorkshopDocument doc = createSampleDocument(WORKSHOP_ID, 50);

        searchSyncService.handleWorkshopPublished(doc);

        verify(workshopIndexStore).upsert(doc);
        verify(workshopCapacityStore).set(WORKSHOP_ID, 50);
        verifyNoInteractions(searchCacheInvalidator);
    }

    @Test
    @DisplayName(
        "handleWorkshopUpdated: upserts ES document and invalidates search cache"
    )
    void shouldUpsertDocumentAndInvalidateCacheOnUpdated() {
        WorkshopDocument doc = createSampleDocument(WORKSHOP_ID, 60);

        searchSyncService.handleWorkshopUpdated(doc);

        verify(workshopIndexStore).upsert(doc);
        verify(searchCacheInvalidator).invalidateAll();
        verifyNoInteractions(workshopCapacityStore);
    }

    @Test
    @DisplayName(
        "handleWorkshopRescheduled: updates schedule in ES and invalidates search cache"
    )
    void shouldUpdateScheduleAndInvalidateCacheOnRescheduled() {
        Instant newStartsAt = Instant.now().plusSeconds(7200);
        Instant newEndsAt = newStartsAt.plusSeconds(3600);
        String timezone = "Asia/Ho_Chi_Minh";

        searchSyncService.handleWorkshopRescheduled(
            WORKSHOP_ID,
            newStartsAt,
            newEndsAt,
            timezone
        );

        verify(workshopIndexStore).updateSchedule(
            WORKSHOP_ID,
            newStartsAt,
            newEndsAt,
            timezone
        );
        verify(searchCacheInvalidator).invalidateAll();
        verifyNoInteractions(workshopCapacityStore);
    }

    @Test
    @DisplayName(
        "handleWorkshopCompleted: deletes document from ES and invalidates search cache"
    )
    void shouldDeleteDocumentAndInvalidateCacheOnCompleted() {
        searchSyncService.handleWorkshopCompleted(WORKSHOP_ID);

        verify(workshopIndexStore).delete(WORKSHOP_ID);
        verify(searchCacheInvalidator).invalidateAll();
        verifyNoInteractions(workshopCapacityStore);
    }

    @Test
    @DisplayName(
        "handleWorkshopCanceled: deletes document from ES and invalidates search cache"
    )
    void shouldDeleteDocumentAndInvalidateCacheOnCanceled() {
        searchSyncService.handleWorkshopCanceled(WORKSHOP_ID);

        verify(workshopIndexStore).delete(WORKSHOP_ID);
        verify(searchCacheInvalidator).invalidateAll();
        verifyNoInteractions(workshopCapacityStore);
    }
}
