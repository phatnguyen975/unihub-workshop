package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.JpaKeysetPagination;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;
import com.unihub.workshop.workshops.domain.model.StaffWorkshopFilter;
import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopAssignmentPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
            "assignedAt",
            null
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
    public void deleteByWorkshopIdAndStaffUserId(
        UUID workshopId,
        UUID staffUserId
    ) {
        if (workshopId != null && staffUserId != null) {
            repository.deleteByWorkshopIdAndStaffUserId(
                workshopId,
                staffUserId
            );
        }
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
        Map<UUID, Integer> result = new HashMap<>(staffUserIds.size());
        for (UUID userId : staffUserIds) {
            result.put(userId, 0);
        }
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
            primaryVal,
            null
        );
        return KeysetCursorCodec.encode(cursor);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<StaffAssignedWorkshop> findAssignedWorkshopsByStaffUserId(
        UUID staffUserId,
        String cursorString,
        int size,
        StaffWorkshopFilter filter
    ) {
        if (staffUserId == null) {
            return new CursorPage<>(Collections.emptyList(), null, false);
        }

        String filterFingerprint =
            filter != null
                ? filter.toFingerprint()
                : StaffWorkshopFilter.empty().toFingerprint();

        KeysetCursor cursor = KeysetCursorCodec.decode(
            cursorString,
            "startsAt",
            filterFingerprint
        );

        Instant cursorStartsAt = cursor != null ? cursor.asInstant() : null;
        UUID cursorId = cursor != null ? cursor.id() : null;

        StringBuilder sql = new StringBuilder(
            """
                SELECT
                    w.id AS workshop_id,
                    w.title,
                    w.slug,
                    w.description,
                    w.cover_image_url,
                    w.location,
                    w.starts_at,
                    w.ends_at,
                    w.status,
                    o.name AS organization_name,
                    a.assigned_at
                FROM workshop_assignments a
                INNER JOIN workshops w ON a.workshop_id = w.id
                INNER JOIN organizations o ON w.organization_id = o.id
                WHERE a.staff_user_id = :staffUserId
                  AND w.status <> 'CANCELED'
            """
        );

        Map<String, Object> params = new HashMap<>();
        params.put("staffUserId", staffUserId);

        if (filter != null) {
            if (filter.status() != null) {
                sql.append(" AND w.status = :status");
                params.put("status", filter.status().name());
            }
            if (filter.from() != null) {
                sql.append(" AND w.starts_at >= :fromTimestamp");
                params.put(
                    "fromTimestamp",
                    java.sql.Timestamp.from(filter.from())
                );
            }
            if (filter.to() != null) {
                sql.append(" AND w.starts_at <= :toTimestamp");
                params.put("toTimestamp", java.sql.Timestamp.from(filter.to()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                sql.append(
                    " AND (LOWER(w.title) LIKE :qPattern OR LOWER(w.slug) LIKE :qPattern OR (w.location IS NOT NULL AND LOWER(w.location) LIKE :qPattern))"
                );
                params.put("qPattern", "%" + filter.q().toLowerCase() + "%");
            }
        }

        // Add keyset pagination only when a cursor exists
        if (cursor != null && cursorId != null) {
            if (cursorStartsAt != null) {
                // Cursor points to a workshop with a non-null starts_at
                sql.append(
                    """
                    AND (
                        w.starts_at > :cursorStartsAt
                        OR (
                            w.starts_at = :cursorStartsAt
                            AND w.id > :cursorId
                        )
                        OR w.starts_at IS NULL
                    )
                    """
                );

                params.put(
                    "cursorStartsAt",
                    java.sql.Timestamp.from(cursorStartsAt)
                );
                params.put("cursorId", cursorId);
            } else {
                // Cursor points to a workshop with a null starts_at
                sql.append(
                    """
                    AND w.starts_at IS NULL
                    AND w.id > :cursorId
                    """
                );

                params.put("cursorId", cursorId);
            }
        }

        sql.append(
            " ORDER BY w.starts_at ASC NULLS LAST, w.id ASC" +
                " LIMIT :queryLimit"
        );

        Query query = entityManager.createNativeQuery(sql.toString());

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        query.setParameter("queryLimit", size + 1);

        List<?> rawRows = query.getResultList();

        boolean hasMore = rawRows.size() > size;
        List<?> pageRows = hasMore ? rawRows.subList(0, size) : rawRows;

        List<StaffAssignedWorkshop> items = new ArrayList<>(pageRows.size());
        for (Object rowObj : pageRows) {
            if (rowObj instanceof Object[] row) {
                UUID id = toUuid(row[0]);
                String title = row[1] != null ? row[1].toString() : null;
                String slug = row[2] != null ? row[2].toString() : null;
                String description = row[3] != null ? row[3].toString() : null;
                String coverImageUrl =
                    row[4] != null ? row[4].toString() : null;
                String location = row[5] != null ? row[5].toString() : null;
                Instant startsAt = toInstant(row[6]);
                Instant endsAt = toInstant(row[7]);
                String statusStr = row[8] != null ? row[8].toString() : null;
                WorkshopStatus status =
                    statusStr != null
                        ? WorkshopStatus.valueOf(statusStr)
                        : null;
                String organizationName =
                    row[9] != null ? row[9].toString() : null;
                Instant assignedAt = toInstant(row[10]);

                items.add(
                    new StaffAssignedWorkshop(
                        id,
                        title,
                        slug,
                        description,
                        coverImageUrl,
                        location,
                        startsAt,
                        endsAt,
                        status,
                        organizationName,
                        assignedAt
                    )
                );
            }
        }

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            StaffAssignedWorkshop lastItem = items.get(items.size() - 1);
            String primaryVal =
                lastItem.startsAt() != null
                    ? lastItem.startsAt().toString()
                    : null;
            KeysetCursor nextKeysetCursor = new KeysetCursor(
                lastItem.id(),
                "startsAt",
                primaryVal,
                null,
                filterFingerprint
            );
            nextCursor = KeysetCursorCodec.encode(nextKeysetCursor);
        }

        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private static UUID toUuid(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(obj.toString());
    }

    private static Instant toInstant(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Instant instant) {
            return instant;
        }
        if (obj instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (obj instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return Instant.parse(obj.toString());
    }
}
