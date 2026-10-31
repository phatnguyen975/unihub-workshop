package com.unihub.workshop.attendances.application.policy;

import com.unihub.workshop.attendances.domain.model.AttendanceClassification;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.domain.model.SyncEntry;
import com.unihub.workshop.registrations.api.dto.RegistrationTicketContextDto;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure reconciliation policy: decides which submitted entries may be persisted and
 * which verdict each entry receives.
 *
 * <p>This type deliberately has zero framework dependencies. It is not a Spring bean;
 * the use case owns a single stateless instance.
 *
 * <p>It lives in the application layer, rather than the domain layer, because it
 * consumes the {@code registrations} module's public DTO, and the domain layer is
 * forbidden from depending on any {@code ..api..} package.
 */
public final class AttendanceReconciliationPolicy {

    private static final String CONFIRMED_STATUS = "CONFIRMED";

    /**
     * Resolves which submitted registrations are admissible for check-in.
     *
     * <p>A registration is admissible only when it exists, its status is
     * {@code CONFIRMED}, and it belongs to the workshop targeted by the request.
     *
     * @param entries submitted batch entries
     * @param ticketContexts registration context resolved in one bulk read
     * @param workshopId workshop targeted by the request
     * @return identifiers of the admissible registrations
     */
    public Set<UUID> resolveAdmissibleRegistrationIds(
        Collection<SyncEntry> entries,
        Map<UUID, RegistrationTicketContextDto> ticketContexts,
        UUID workshopId
    ) {
        if (entries == null || entries.isEmpty()) {
            return Set.of();
        }
        Map<UUID, RegistrationTicketContextDto> tickets =
            ticketContexts != null ? ticketContexts : Map.of();
        Set<UUID> admissible = new HashSet<>();
        for (SyncEntry entry : entries) {
            RegistrationTicketContextDto ticket = tickets.get(
                entry.registrationId()
            );
            if (ticket == null) {
                continue;
            }
            if (
                ticket.status() == null ||
                !CONFIRMED_STATUS.equalsIgnoreCase(ticket.status())
            ) {
                continue;
            }
            if (!Objects.equals(workshopId, ticket.workshopId())) {
                continue;
            }
            admissible.add(entry.registrationId());
        }
        return Set.copyOf(admissible);
    }

    /**
     * Classifies every submitted entry, preserving the submitted order.
     *
     * <p>An entry is {@code SUCCESS} only when it was admissible and its {@code syncId}
     * came back from the bulk write. Any other admissible entry lost the race or reused
     * a {@code syncId}, so it is {@code ALREADY_USED}. An inadmissible entry is always
     * {@code INVALID_TICKET}, regardless of the write outcome.
     *
     * @param entries submitted batch entries
     * @param admissibleRegistrationIds registrations that passed ticket validation
     * @param persistedSyncIds sync identifiers actually persisted by the bulk write
     * @return one outcome per submitted entry, in the submitted order
     */
    public List<AttendanceOutcome> classify(
        List<SyncEntry> entries,
        Set<UUID> admissibleRegistrationIds,
        Set<UUID> persistedSyncIds
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Set<UUID> admissible =
            admissibleRegistrationIds != null
                ? admissibleRegistrationIds
                : Set.of();
        Set<UUID> persisted =
            persistedSyncIds != null ? persistedSyncIds : Set.of();
        List<AttendanceOutcome> outcomes = new ArrayList<>(entries.size());
        for (SyncEntry entry : entries) {
            if (!admissible.contains(entry.registrationId())) {
                outcomes.add(
                    new AttendanceOutcome(
                        entry.syncId(),
                        AttendanceClassification.INVALID_TICKET
                    )
                );
            } else if (persisted.contains(entry.syncId())) {
                outcomes.add(
                    new AttendanceOutcome(
                        entry.syncId(),
                        AttendanceClassification.SUCCESS
                    )
                );
            } else {
                outcomes.add(
                    new AttendanceOutcome(
                        entry.syncId(),
                        AttendanceClassification.ALREADY_USED
                    )
                );
            }
        }
        return List.copyOf(outcomes);
    }
}
