package com.unihub.workshop.workshops.infrastructure.cache;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.unihub.workshop.shared.events.WorkshopCompletedEvent;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAvailabilityPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkshopLifecycleCacheEvictionListener Unit Tests")
class WorkshopLifecycleCacheEvictionListenerTest {

    @Mock
    private WorkshopAvailabilityPort workshopAvailabilityPort;

    private WorkshopLifecycleCacheEvictionListener listener;

    @BeforeEach
    void setUp() {
        listener = new WorkshopLifecycleCacheEvictionListener(
            workshopAvailabilityPort
        );
    }

    @Test
    @DisplayName(
        "Deletes capacity counter when valid WorkshopCompletedEvent is received"
    )
    void onWorkshopCompleted_validEvent_callsDeleteCapacity() {
        UUID workshopId = UUID.randomUUID();
        WorkshopCompletedEvent event = new WorkshopCompletedEvent(
            workshopId,
            UUID.randomUUID(),
            "Title",
            "slug",
            Instant.now().minusSeconds(3600),
            Instant.now(),
            Instant.now()
        );

        listener.onWorkshopCompleted(event);

        verify(workshopAvailabilityPort).deleteCapacity(workshopId);
    }

    @Test
    @DisplayName("Does nothing when event is null")
    void onWorkshopCompleted_nullEvent_noInteractions() {
        listener.onWorkshopCompleted(null);

        verifyNoInteractions(workshopAvailabilityPort);
    }

    @Test
    @DisplayName("Does nothing when workshopId in event is null")
    void onWorkshopCompleted_nullWorkshopId_noInteractions() {
        WorkshopCompletedEvent event = new WorkshopCompletedEvent(
            null,
            UUID.randomUUID(),
            "Title",
            "slug",
            Instant.now().minusSeconds(3600),
            Instant.now(),
            Instant.now()
        );

        listener.onWorkshopCompleted(event);

        verifyNoInteractions(workshopAvailabilityPort);
    }
}
