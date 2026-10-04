package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.events.WorkshopCompletedEvent;
import com.unihub.workshop.workshops.application.ports.in.SweepWorkshopLifecycleUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopLockPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.CompletedWorkshopDetails;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Slf4j
@Service
@RequiredArgsConstructor
public class SweepWorkshopLifecycleUseCaseImpl
    implements SweepWorkshopLifecycleUseCase
{

    private static final String LOCK_KEY = "lock:sweeper:lifecycle";
    private static final Duration LOCK_TTL = Duration.ofSeconds(240);

    private final WorkshopLockPort workshopLockPort;
    private final WorkshopRepository workshopRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactionOperations;

    @Override
    public void sweepLifecycle() {
        boolean acquired = workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL);
        if (!acquired) {
            log.debug(
                "Lifecycle sweeper skipped: lock '{}' is currently held by another node",
                LOCK_KEY
            );
            return;
        }

        try {
            transactionOperations.executeWithoutResult(status ->
                executeSweep()
            );
        } catch (Exception e) {
            log.warn(
                "Lifecycle sweeper failed during database transition: {}",
                e.getMessage(),
                e
            );
            throw e;
        } finally {
            workshopLockPort.releaseLock(LOCK_KEY);
        }
    }

    private void executeSweep() {
        Instant now = Instant.now();
        List<CompletedWorkshopDetails> completedWorkshops =
            workshopRepository.completeExpiredWorkshops(now);

        if (completedWorkshops.isEmpty()) {
            log.debug(
                "Lifecycle sweeper run completed: 0 workshops transitioned to COMPLETED"
            );
            return;
        }

        for (CompletedWorkshopDetails w : completedWorkshops) {
            WorkshopCompletedEvent event = new WorkshopCompletedEvent(
                w.id(),
                w.organizationId(),
                w.title(),
                w.slug(),
                w.startsAt(),
                w.endsAt(),
                w.completedAt()
            );
            eventPublisher.publishEvent(event);
            log.info(
                "Workshop {} transitioned to COMPLETED by lifecycle sweeper",
                w.id()
            );
        }

        log.info(
            "Lifecycle sweeper successfully completed {} workshops at {}",
            completedWorkshops.size(),
            now
        );
    }
}
