package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.workshops.application.ports.out.WorkshopRegistrationCountPort;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
