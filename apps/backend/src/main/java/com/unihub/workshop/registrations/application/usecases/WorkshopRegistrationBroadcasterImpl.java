package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.ports.out.WorkshopRegistrationBroadcaster;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationRawCounts;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationSeatCounts;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Implementation of {@link WorkshopRegistrationBroadcaster}.
 * Computes the 6-field seat count snapshot on registration state changes
 * and broadcasts a {@code registration.updated} event to the workshop channel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopRegistrationBroadcasterImpl
    implements WorkshopRegistrationBroadcaster
{

    private final RegistrationRepository registrationRepository;
    private final WorkshopApi workshopApi;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Override
    public void broadcast(UUID workshopId) {
        if (workshopId == null) {
            log.warn(
                "Cannot broadcast registration seat counts: workshopId is null"
            );
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        executeBroadcast(workshopId);
                    }
                }
            );
        } else {
            executeBroadcast(workshopId);
        }
    }

    private void executeBroadcast(UUID workshopId) {
        try {
            Optional<WorkshopRegistrationDetailsDto> optWorkshop =
                workshopApi.getWorkshopForRegistration(workshopId);
            if (optWorkshop.isEmpty()) {
                log.warn(
                    "Cannot broadcast registration seat counts: workshop [{}] not found",
                    workshopId
                );
                return;
            }

            WorkshopRegistrationDetailsDto workshop = optWorkshop.get();
            WorkshopRegistrationRawCounts rawCounts =
                registrationRepository.getRegistrationRawCounts(workshopId);

            int capacity =
                workshop.capacity() != null ? workshop.capacity() : 0;
            Instant lastUpdatedAt = rawCounts
                .latestUpdatedAt()
                .orElse(
                    workshop.startsAt() != null
                        ? workshop.startsAt()
                        : Instant.now()
                );

            WorkshopRegistrationSeatCounts seatCounts =
                WorkshopRegistrationSeatCounts.of(
                    workshop.id(),
                    capacity,
                    rawCounts.confirmedCount(),
                    rawCounts.pendingCount(),
                    lastUpdatedAt
                );

            Map<String, Object> payload = Map.of(
                "workshopId",
                workshop.id(),
                "confirmedCount",
                seatCounts.confirmedCount(),
                "pendingCount",
                seatCounts.pendingCount(),
                "capacity",
                seatCounts.capacity(),
                "availableSeats",
                seatCounts.availableSeats(),
                "lastUpdatedAt",
                seatCounts.lastUpdatedAt()
            );

            RealtimeEvent<Map<String, Object>> event = RealtimeEvent.of(
                RealtimeEventType.REGISTRATION_UPDATED,
                payload
            );

            realtimeEventPublisher.publishToWorkshop(workshop.id(), event);
            log.debug(
                "Successfully broadcast 6-field registration.updated event for workshop [{}]",
                workshop.id()
            );
        } catch (Exception ex) {
            log.error(
                "Failed to broadcast registration seat count snapshot for workshop [{}]: {}",
                workshopId,
                ex.getMessage(),
                ex
            );
        }
    }
}
