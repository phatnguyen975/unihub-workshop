package com.unihub.workshop.workshops.application.ports.out;

import com.unihub.workshop.workshops.domain.model.Workshop;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkshopRepository {
    Workshop save(Workshop workshop);

    Optional<Workshop> findById(UUID id);

    Optional<Workshop> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsBySlug(String slug);

    List<String> findConflictingSpeakerEmails(
        Collection<String> emails,
        Instant startsAt,
        Instant endsAt,
        UUID excludeWorkshopId
    );

    void deleteById(UUID id);
}
