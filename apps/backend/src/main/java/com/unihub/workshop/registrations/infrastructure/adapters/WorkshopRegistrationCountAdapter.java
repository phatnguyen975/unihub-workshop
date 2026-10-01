package com.unihub.workshop.registrations.infrastructure.adapters;

import com.unihub.workshop.workshops.api.WorkshopRegistrationCountPort;
import jakarta.persistence.EntityManager;
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
}
