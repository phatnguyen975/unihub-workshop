package com.unihub.workshop.attendances.application.events;

import com.unihub.workshop.attendances.application.ports.out.AttendanceRepository;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes the live attendance counter snapshot of a workshop.
 *
 * <p>The event is always produced <em>after</em> the reconciliation transaction has
 * committed, so a counter update can never describe check-ins that were rolled
 * back. Registration happens on the transaction synchronization, which is also
 * why a rollback simply drops the callback: {@code afterCommit} is never invoked.
 *
 * <p>The payload is read at publish time rather than during the transaction,
 * because the count must include the rows the transaction just inserted. The
 * read therefore runs on the primary database (the default routing target
 * outside a read-only transaction), never on the lagging replica.
 *
 * <p>Broadcasting is best effort: once the check-ins are committed, a failure to
 * read the counter or to publish must not turn a successful sync into an error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceSyncEventPublisher {

    private static final int UNKNOWN_CAPACITY = 0;

    private final AttendanceRepository attendanceRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    /**
     * Schedules one {@code attendance.updated} event for the end of the current
     * transaction, or publishes it immediately when no transaction is active.
     *
     * @param workshopId workshop whose counter changed
     * @param capacity   workshop capacity, or {@code null} when unknown
     */
    public void publishAfterCommit(UUID workshopId, Integer capacity) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publish(workshopId, capacity);
                    }
                }
            );
            return;
        }
        publish(workshopId, capacity);
    }

    private void publish(UUID workshopId, Integer capacity) {
        try {
            long checkedInCount = attendanceRepository.countValidByWorkshopId(
                workshopId
            );

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("workshopId", workshopId);
            payload.put("checkedInCount", checkedInCount);
            payload.put(
                "capacity",
                capacity != null ? capacity : UNKNOWN_CAPACITY
            );
            payload.put("lastSyncedAt", Instant.now());

            realtimeEventPublisher.publishToWorkshop(
                workshopId,
                RealtimeEvent.of(RealtimeEventType.ATTENDANCE_UPDATED, payload)
            );
        } catch (RuntimeException ex) {
            log.error(
                "Failed to publish attendance.updated for workshop [{}]: {}",
                workshopId,
                ex.getMessage(),
                ex
            );
        }
    }
}
