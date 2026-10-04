package com.unihub.workshop.registrations.infrastructure.adapters;

import com.unihub.workshop.workshops.api.WorkshopRegistrationCountPort;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Downstream persistence adapter implementing the {@link WorkshopRegistrationCountPort} SPI.
 * <p>
 * Queries the {@code registrations} table owned by this module to return the active
 * registration count for a workshop. This honors Bounded Context data ownership and eliminates
 * cross-module database backdoor access.
 */
@Component
@RequiredArgsConstructor
public class WorkshopRegistrationCountAdapter
    implements WorkshopRegistrationCountPort
{

    private final EntityManager entityManager;

    @Override
    public long countActiveRegistrations(UUID workshopId) {
        if (workshopId == null) {
            return 0L;
        }

        Number count = (Number) entityManager
            .createNativeQuery(
                "SELECT count(r.id) FROM registrations r WHERE r.workshop_id = :workshopId AND r.status IN ('PENDING', 'CONFIRMED')"
            )
            .setParameter("workshopId", workshopId)
            .getSingleResult();

        return count != null ? count.longValue() : 0L;
    }

    @Override
    public Map<UUID, Long> countActiveRegistrationsBatch(
        List<UUID> workshopIds
    ) {
        if (workshopIds == null || workshopIds.isEmpty()) {
            return Map.of();
        }

        List<?> rows = entityManager
            .createNativeQuery(
                "SELECT r.workshop_id, count(r.id) FROM registrations r " +
                    "WHERE r.workshop_id IN (:workshopIds) AND r.status IN ('PENDING', 'CONFIRMED') " +
                    "GROUP BY r.workshop_id"
            )
            .setParameter("workshopIds", workshopIds)
            .getResultList();

        Map<UUID, Long> result = new HashMap<>();
        for (Object item : rows) {
            if (item instanceof Object[] row && row.length >= 2) {
                UUID id =
                    row[0] instanceof UUID u
                        ? u
                        : row[0] != null
                          ? UUID.fromString(row[0].toString())
                          : null;
                Long count = row[1] instanceof Number n ? n.longValue() : 0L;
                if (id != null) {
                    result.put(id, count);
                }
            }
        }
        return result;
    }
}
