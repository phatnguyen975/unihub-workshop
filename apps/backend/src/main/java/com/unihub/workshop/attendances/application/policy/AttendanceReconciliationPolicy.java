package com.unihub.workshop.attendances.application.policy;

import com.unihub.workshop.attendances.domain.model.Attendance;
import com.unihub.workshop.attendances.domain.model.AttendanceClassification;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.domain.model.AttendanceStatus;
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
     * Selects the entries the reconciliation must attempt to persist.
     *
     * <p>An entry qualifies only when its registration is admissible and no
     * attendance record exists yet for that registration. A record that already
     * exists — {@code VALID} or {@code INVALIDATED} — is never rewritten or
     * reactivated, so it removes the entry from the insert set.
     *
     * @param entries submitted batch entries
     * @param admissibleRegistrationIds registrations that passed ticket validation
     * @param existingByRegistrationId attendance records already stored for the batch
     * @return the entries to insert, in the submitted order
     */
    public List<SyncEntry> resolveEntriesToInsert(
        List<SyncEntry> entries,
        Set<UUID> admissibleRegistrationIds,
        Map<UUID, Attendance> existingByRegistrationId
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Set<UUID> admissible =
            admissibleRegistrationIds != null
                ? admissibleRegistrationIds
                : Set.of();
        Map<UUID, Attendance> existing =
            existingByRegistrationId != null
                ? existingByRegistrationId
                : Map.of();
        List<SyncEntry> toInsert = new ArrayList<>(entries.size());
        for (SyncEntry entry : entries) {
            if (!admissible.contains(entry.registrationId())) {
                continue;
            }
            if (existing.containsKey(entry.registrationId())) {
                continue;
            }
            toInsert.add(entry);
        }
        return List.copyOf(toInsert);
    }

    /**
     * Classifies every submitted entry, preserving the submitted order.
     *
     * <p>The verdict follows a fixed precedence: an invalid ticket always wins;
     * then an existing {@code INVALIDATED} record, which is terminal for this flow;
     * then an existing {@code VALID} record carrying the same {@code syncId}, which
     * means the scan was already applied; then an existing {@code VALID} record
     * carrying a different {@code syncId}, which is a double scan; and only then a
     * new insert, whose verdict depends on whether the bulk write persisted it.
     *
     * @param entries submitted batch entries
     * @param admissibleRegistrationIds registrations that passed ticket validation
     * @param existingByRegistrationId attendance records already stored for the batch
     * @param persistedSyncIds sync identifiers actually persisted by the bulk write
     * @return one outcome per submitted entry, in the submitted order
     */
    public List<AttendanceOutcome> classify(
        List<SyncEntry> entries,
        Set<UUID> admissibleRegistrationIds,
        Map<UUID, Attendance> existingByRegistrationId,
        Set<UUID> persistedSyncIds
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Set<UUID> admissible =
            admissibleRegistrationIds != null
                ? admissibleRegistrationIds
                : Set.of();
        Map<UUID, Attendance> existing =
            existingByRegistrationId != null
                ? existingByRegistrationId
                : Map.of();
        Set<UUID> persisted =
            persistedSyncIds != null ? persistedSyncIds : Set.of();
        List<AttendanceOutcome> outcomes = new ArrayList<>(entries.size());
        for (SyncEntry entry : entries) {
            outcomes.add(
                new AttendanceOutcome(
                    entry.syncId(),
                    classifyEntry(entry, admissible, existing, persisted)
                )
            );
        }
        return List.copyOf(outcomes);
    }

    private static AttendanceClassification classifyEntry(
        SyncEntry entry,
        Set<UUID> admissibleRegistrationIds,
        Map<UUID, Attendance> existingByRegistrationId,
        Set<UUID> persistedSyncIds
    ) {
        if (!admissibleRegistrationIds.contains(entry.registrationId())) {
            return AttendanceClassification.INVALID_TICKET;
        }

        Attendance existing = existingByRegistrationId.get(
            entry.registrationId()
        );
        if (existing != null) {
            if (existing.status() != AttendanceStatus.VALID) {
                return AttendanceClassification.ALREADY_USED;
            }
            return Objects.equals(entry.syncId(), existing.syncId())
                ? AttendanceClassification.SUCCESS
                : AttendanceClassification.ALREADY_USED;
        }

        return persistedSyncIds.contains(entry.syncId())
            ? AttendanceClassification.SUCCESS
            : AttendanceClassification.ALREADY_USED;
    }
}
