package com.unihub.workshop.workshops.application.ports.out;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsQuery;
import com.unihub.workshop.workshops.domain.model.ActiveWorkshopCapacity;
import com.unihub.workshop.workshops.domain.model.CompletedWorkshopDetails;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopSummary;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkshopRepository {
    Workshop save(Workshop workshop);

    Optional<Workshop> findById(UUID id);

    List<Workshop> findAllById(Collection<UUID> ids);

    Optional<Workshop> findByIdForUpdate(UUID id);

    Optional<Workshop> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsBySlug(String slug);

    Optional<Workshop> findBySlug(String slug);

    List<String> findConflictingSpeakerEmails(
        Collection<String> emails,
        Instant startsAt,
        Instant endsAt,
        UUID excludeWorkshopId
    );

    Optional<UUID> findCollidingWorkshopAtLocation(
        String location,
        Instant startsAt,
        Instant endsAt,
        UUID excludeWorkshopId
    );

    void deleteById(UUID id);

    CursorPage<WorkshopSummary> findAllByOrganizationWithFilters(
        ListAdminWorkshopsQuery query
    );

    List<CompletedWorkshopDetails> completeExpiredWorkshops(Instant now);

    List<ActiveWorkshopCapacity> findActivePublishedWorkshops(Instant now);
}
