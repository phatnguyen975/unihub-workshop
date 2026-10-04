package com.unihub.workshop.workshops.infrastructure.cache;

import com.unihub.workshop.shared.events.WorkshopCompletedEvent;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAvailabilityPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopLifecycleCacheEvictionListener {

    private final WorkshopAvailabilityPort workshopAvailabilityPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkshopCompleted(WorkshopCompletedEvent event) {
        if (event == null || event.workshopId() == null) {
            return;
        }

        try {
            workshopAvailabilityPort.deleteCapacity(event.workshopId());
            log.info(
                "Post-commit evicted Redis availability counter for completed workshop {}",
                event.workshopId()
            );
        } catch (Exception e) {
            log.warn(
                "Failed to evict Redis availability counter for completed workshop {}: {}",
                event.workshopId(),
                e.getMessage()
            );
        }
    }
}
