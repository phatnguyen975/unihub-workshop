package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.JpaKeysetPagination;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopAssignmentPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WorkshopAssignmentRepositoryAdapter
    implements WorkshopAssignmentRepository
{

    private final WorkshopAssignmentJpaRepository repository;
    private final WorkshopAssignmentPersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<WorkshopAssignment> findByWorkshopId(
        UUID workshopId,
        String cursorString,
        int size
    ) {
        KeysetCursor cursor = KeysetCursorCodec.decode(
            cursorString,
            "assignedAt"
        );

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<WorkshopAssignmentJpaEntity> cq = cb.createQuery(
            WorkshopAssignmentJpaEntity.class
        );
        Root<WorkshopAssignmentJpaEntity> root = cq.from(
            WorkshopAssignmentJpaEntity.class
        );

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("workshopId"), workshopId));

        Path<UUID> idPath = root.get("id");
        Path<Instant> assignedAtPath = root.get("assignedAt");

        if (cursor != null) {
            Predicate keysetPredicate =
                JpaKeysetPagination.buildKeysetPredicate(
                    cb,
                    assignedAtPath,
                    cursor.asInstant(),
                    true,
                    assignedAtPath,
                    cursor.asInstant(),
                    idPath,
                    cursor.id()
                );
            if (keysetPredicate != null) {
                predicates.add(keysetPredicate);
            }
        }

        cq.where(predicates.toArray(new Predicate[0]));

        JpaKeysetPagination.applyOrders(
            cb,
            cq,
            assignedAtPath,
            true,
            null,
            idPath
        );

        JpaKeysetPagination.KeysetSlice<WorkshopAssignmentJpaEntity> slice =
            JpaKeysetPagination.execute(
                entityManager,
                cq,
                size > 0 ? size : 20
            );

        List<WorkshopAssignmentJpaEntity> entities = slice.items();
        List<WorkshopAssignment> items = mapper.toDomainList(entities);

        String nextCursor = null;
        if (slice.hasMore() && !entities.isEmpty()) {
            nextCursor = encodeCursor(entities.get(entities.size() - 1));
        }

        return new CursorPage<>(items, nextCursor, slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByWorkshopIdAndStaffUserId(
        UUID workshopId,
        UUID staffUserId
    ) {
        return repository.existsByWorkshopIdAndStaffUserId(
            workshopId,
            staffUserId
        );
    }

    @Override
    @Transactional
    public WorkshopAssignment save(WorkshopAssignment assignment) {
        WorkshopAssignmentJpaEntity entity = mapper.toEntity(assignment);
        WorkshopAssignmentJpaEntity saved = repository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public List<WorkshopAssignment> saveAll(
        List<WorkshopAssignment> assignments
    ) {
        if (assignments == null || assignments.isEmpty()) {
            return Collections.emptyList();
        }
        List<WorkshopAssignmentJpaEntity> entities = assignments
            .stream()
            .map(mapper::toEntity)
            .toList();
        List<WorkshopAssignmentJpaEntity> saved = repository.saveAllAndFlush(
            entities
        );
        return mapper.toDomainList(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findAlreadyAssignedUserIds(
        UUID workshopId,
        Collection<UUID> staffUserIds
    ) {
        if (
            workshopId == null || staffUserIds == null || staffUserIds.isEmpty()
        ) {
            return Collections.emptySet();
        }
        return repository.findAlreadyAssignedUserIds(workshopId, staffUserIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findScheduleConflictingUserIds(
        UUID organizationId,
        UUID targetWorkshopId,
        Instant startsAt,
        Instant endsAt,
        Collection<UUID> staffUserIds
    ) {
        if (
            organizationId == null ||
            targetWorkshopId == null ||
            startsAt == null ||
            endsAt == null ||
            staffUserIds == null ||
            staffUserIds.isEmpty()
        ) {
            return Collections.emptySet();
        }
        return repository.findScheduleConflictingUserIds(
            organizationId,
            targetWorkshopId,
            startsAt,
            endsAt,
            staffUserIds
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findAssignedStaffUserIds(UUID workshopId) {
        if (workshopId == null) {
            return Collections.emptySet();
        }
        return repository.findAssignedStaffUserIds(workshopId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findScheduleConflictingStaffUserIds(
        UUID organizationId,
        UUID targetWorkshopId,
        Instant startsAt,
        Instant endsAt
    ) {
        if (
            organizationId == null ||
            targetWorkshopId == null ||
            startsAt == null ||
            endsAt == null
        ) {
            return Collections.emptySet();
        }
        return repository.findScheduleConflictingStaffUserIds(
            organizationId,
            targetWorkshopId,
            startsAt,
            endsAt
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> countActiveAssignmentsForUsers(
        UUID organizationId,
        Collection<UUID> staffUserIds
    ) {
        if (
            organizationId == null ||
            staffUserIds == null ||
            staffUserIds.isEmpty()
        ) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = repository.countActiveAssignmentsForUsers(
            organizationId,
            staffUserIds
        );
        Map<UUID, Integer> result = new java.util.HashMap<>();
        for (Object[] row : rows) {
            UUID userId = (UUID) row[0];
            Number count = (Number) row[1];
            result.put(userId, count != null ? count.intValue() : 0);
        }
        return result;
    }

    private String encodeCursor(WorkshopAssignmentJpaEntity entity) {
        String primaryVal =
            entity.getAssignedAt() != null
                ? entity.getAssignedAt().toString()
                : null;
        KeysetCursor cursor = new KeysetCursor(
            entity.getId(),
            "assignedAt",
            primaryVal,
            primaryVal
        );
        return KeysetCursorCodec.encode(cursor);
    }
}
