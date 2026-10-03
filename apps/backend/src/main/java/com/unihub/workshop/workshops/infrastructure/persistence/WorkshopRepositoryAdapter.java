package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.JpaKeysetPagination;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.domain.model.WorkshopSummary;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WorkshopRepositoryAdapter implements WorkshopRepository {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "createdAt",
        "startsAt",
        "price",
        "capacity"
    );

    private final WorkshopJpaRepository repository;
    private final WorkshopSpeakerJpaRepository speakerRepository;
    private final WorkshopPersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Workshop save(Workshop workshop) {
        WorkshopJpaEntity entity = mapper.toEntity(workshop);
        try {
            boolean exists =
                entity.getId() != null && repository.existsById(entity.getId());
            WorkshopJpaEntity saved;
            if (!exists) {
                entity.setVersion(null);
                entityManager.persist(entity);
                entityManager.flush();
                if (entity.getVersion() == null) {
                    entity.setVersion(0);
                }
                saved = entity;
            } else {
                saved = repository.saveAndFlush(entity);
            }

            if (workshop.speakers() != null) {
                speakerRepository.deleteByWorkshopId(saved.getId());
                if (!workshop.speakers().isEmpty()) {
                    List<WorkshopSpeakerJpaEntity> speakerEntities = workshop
                        .speakers()
                        .stream()
                        .map(s -> {
                            WorkshopSpeakerJpaEntity se =
                                mapper.toSpeakerEntity(s);
                            se.setWorkshopId(saved.getId());
                            if (se.getId() == null) {
                                se.setId(UUID.randomUUID());
                            }
                            if (se.getCreatedBy() == null) {
                                se.setCreatedBy(saved.getCreatedBy());
                            }
                            if (se.getUpdatedBy() == null) {
                                se.setUpdatedBy(saved.getUpdatedBy());
                            }
                            if (se.getCreatedAt() == null) {
                                se.setCreatedAt(Instant.now());
                            }
                            if (se.getUpdatedAt() == null) {
                                se.setUpdatedAt(Instant.now());
                            }
                            return se;
                        })
                        .toList();
                    speakerRepository.saveAllAndFlush(speakerEntities);
                }
            }

            return mapToDomainWithSpeakers(saved);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException(
                "Workshop version conflict occurred while saving: " +
                    ex.getMessage()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workshop> findById(UUID id) {
        return repository.findById(id).map(this::mapToDomainWithSpeakers);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workshop> findByIdAndOrganizationId(
        UUID id,
        UUID organizationId
    ) {
        return repository
            .findByIdAndOrganizationId(id, organizationId)
            .map(this::mapToDomainWithSpeakers);
    }

    private Workshop mapToDomainWithSpeakers(WorkshopJpaEntity entity) {
        List<WorkshopSpeakerJpaEntity> speakers =
            speakerRepository.findByWorkshopIdOrderBySortOrderAsc(
                entity.getId()
            );
        return mapper
            .toDomain(entity)
            .toBuilder()
            .speakers(mapper.toSpeakerDomainList(speakers))
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        return repository.existsBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workshop> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return repository.findBySlug(slug).map(this::mapToDomainWithSpeakers);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findConflictingSpeakerEmails(
        Collection<String> emails,
        Instant startsAt,
        Instant endsAt,
        UUID excludeWorkshopId
    ) {
        if (
            emails == null ||
            emails.isEmpty() ||
            startsAt == null ||
            endsAt == null
        ) {
            return List.of();
        }
        List<String> normalizedEmails = emails
            .stream()
            .filter(e -> e != null && !e.isBlank())
            .map(String::trim)
            .map(String::toLowerCase)
            .toList();

        if (normalizedEmails.isEmpty()) {
            return List.of();
        }

        if (excludeWorkshopId != null) {
            return speakerRepository.findConflictingSpeakerEmailsExcludingWorkshop(
                normalizedEmails,
                startsAt,
                endsAt,
                excludeWorkshopId
            );
        } else {
            return speakerRepository.findConflictingSpeakerEmails(
                normalizedEmails,
                startsAt,
                endsAt
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCollidingWorkshopAtLocation(
        String location,
        Instant startsAt,
        Instant endsAt,
        UUID excludeWorkshopId
    ) {
        if (
            location == null ||
            location.isBlank() ||
            startsAt == null ||
            endsAt == null
        ) {
            return Optional.empty();
        }
        String normalizedLocation = location.trim();
        List<UUID> collidingIds =
            excludeWorkshopId != null
                ? repository.findCollidingWorkshopIdsAtLocationExcludingWorkshop(
                      normalizedLocation,
                      startsAt,
                      endsAt,
                      excludeWorkshopId
                  )
                : repository.findCollidingWorkshopIdsAtLocation(
                      normalizedLocation,
                      startsAt,
                      endsAt
                  );

        return collidingIds.isEmpty()
            ? Optional.empty()
            : Optional.of(collidingIds.get(0));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        speakerRepository.deleteByWorkshopId(id);
        repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<WorkshopSummary> findAllByOrganizationWithFilters(
        ListAdminWorkshopsQuery query
    ) {
        String sortParam =
            query.sort() != null && !query.sort().isBlank()
                ? query.sort().trim()
                : "createdAt,desc";
        String[] sortParts = sortParam.split(",");
        String sortField = sortParts[0].trim();
        boolean isDesc =
            sortParts.length > 1 &&
            sortParts[1].trim().equalsIgnoreCase("desc");

        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new BadRequestException(
                "Invalid sort field: '" +
                    sortField +
                    "'. Allowed sort fields: " +
                    ALLOWED_SORT_FIELDS
            );
        }

        KeysetCursor cursor = KeysetCursorCodec.decode(
            query.cursor(),
            sortField
        );

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<WorkshopJpaEntity> cq = cb.createQuery(
            WorkshopJpaEntity.class
        );
        Root<WorkshopJpaEntity> root = cq.from(WorkshopJpaEntity.class);

        List<Predicate> predicates = buildPredicates(
            cb,
            root,
            query,
            sortField,
            isDesc,
            cursor
        );
        cq.where(predicates.toArray(new Predicate[0]));

        Path<?> sortPath = root.get(sortField);
        Path<Instant> createdAtPath = root.get("createdAt");
        Path<UUID> idPath = root.get("id");
        JpaKeysetPagination.applyOrders(
            cb,
            cq,
            sortPath,
            isDesc,
            createdAtPath,
            idPath
        );

        int pageSize = query.size() != null ? query.size() : 20;
        JpaKeysetPagination.KeysetSlice<WorkshopJpaEntity> slice =
            JpaKeysetPagination.execute(entityManager, cq, pageSize);

        List<WorkshopJpaEntity> pageEntities = slice.items();
        Map<UUID, Integer> speakerCounts = fetchSpeakerCounts(pageEntities);

        List<WorkshopSummary> items = pageEntities
            .stream()
            .map(entity -> toWorkshopSummary(entity, speakerCounts))
            .toList();

        String nextCursor = null;
        if (slice.hasMore() && !pageEntities.isEmpty()) {
            nextCursor = encodeCursor(
                pageEntities.get(pageEntities.size() - 1),
                sortField
            );
        }

        return new CursorPage<>(items, nextCursor, slice.hasMore());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<Predicate> buildPredicates(
        CriteriaBuilder cb,
        Root<WorkshopJpaEntity> root,
        ListAdminWorkshopsQuery query,
        String sortField,
        boolean isDesc,
        KeysetCursor cursor
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(
            cb.equal(root.get("organizationId"), query.organizationId())
        );

        if (query.status() != null) {
            predicates.add(cb.equal(root.get("status"), query.status().name()));
        }

        if ("FREE".equalsIgnoreCase(query.priceType())) {
            predicates.add(
                cb.or(
                    cb.equal(root.get("price"), BigDecimal.ZERO),
                    cb.isNull(root.get("price"))
                )
            );
        } else if ("PAID".equalsIgnoreCase(query.priceType())) {
            predicates.add(cb.greaterThan(root.get("price"), BigDecimal.ZERO));
        }

        if (query.q() != null && !query.q().isBlank()) {
            String pattern =
                "%" + query.q().trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("slug")), pattern),
                    cb.like(
                        cb.lower(cb.coalesce(root.get("location"), "")),
                        pattern
                    ),
                    cb.like(
                        cb.lower(cb.coalesce(root.get("description"), "")),
                        pattern
                    )
                )
            );
        }

        if (query.from() != null) {
            predicates.add(
                cb.greaterThanOrEqualTo(root.get("startsAt"), query.from())
            );
        }
        if (query.to() != null) {
            predicates.add(
                cb.lessThanOrEqualTo(root.get("startsAt"), query.to())
            );
        }

        if (cursor != null) {
            predicates.add(
                buildKeysetPredicate(cb, root, sortField, isDesc, cursor)
            );
        }

        return predicates;
    }

    /**
     * Dispatches to the generic {@link #buildKeysetBranch} helper.
     * The switch only selects the correct typed Path and cursor value;
     * the actual predicate construction is never duplicated.
     */
    private Predicate buildKeysetPredicate(
        CriteriaBuilder cb,
        Root<WorkshopJpaEntity> root,
        String sortField,
        boolean isDesc,
        KeysetCursor cursor
    ) {
        Path<UUID> idPath = root.get("id");
        Path<Instant> createdAtPath = root.get("createdAt");
        Instant secondaryVal = cursor.asSecondaryInstant();

        return switch (sortField) {
            case "price" -> JpaKeysetPagination.buildKeysetPredicate(
                cb,
                root.get("price"),
                cursor.asBigDecimal(),
                isDesc,
                createdAtPath,
                secondaryVal,
                idPath,
                cursor.id()
            );
            case "capacity" -> JpaKeysetPagination.buildKeysetPredicate(
                cb,
                root.get("capacity"),
                cursor.asInteger(),
                isDesc,
                createdAtPath,
                secondaryVal,
                idPath,
                cursor.id()
            );
            case "startsAt" -> JpaKeysetPagination.buildKeysetPredicate(
                cb,
                root.get("startsAt"),
                cursor.asInstant(),
                isDesc,
                createdAtPath,
                secondaryVal,
                idPath,
                cursor.id()
            );
            default -> JpaKeysetPagination.buildKeysetPredicate(
                cb,
                createdAtPath,
                cursor.asInstant(),
                isDesc,
                createdAtPath,
                cursor.asInstant(),
                idPath,
                cursor.id()
            );
        };
    }

    private Map<UUID, Integer> fetchSpeakerCounts(
        List<WorkshopJpaEntity> entities
    ) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (entities.isEmpty()) {
            return counts;
        }
        List<UUID> ids = entities
            .stream()
            .map(WorkshopJpaEntity::getId)
            .toList();
        for (Object[] row : speakerRepository.countSpeakersByWorkshopIds(ids)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private WorkshopSummary toWorkshopSummary(
        WorkshopJpaEntity entity,
        Map<UUID, Integer> speakerCounts
    ) {
        return new WorkshopSummary(
            entity.getId(),
            entity.getSlug(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getCoverImageUrl(),
            entity.getLocation(),
            entity.getTimezone(),
            entity.getStartsAt(),
            entity.getEndsAt(),
            entity.getCapacity(),
            entity.getPrice(),
            entity.getCurrency(),
            WorkshopStatus.valueOf(entity.getStatus()),
            entity.getVersion(),
            speakerCounts.getOrDefault(entity.getId(), 0),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private String encodeCursor(WorkshopJpaEntity entity, String sortField) {
        String primaryVal = switch (sortField) {
            case "price" -> entity.getPrice() != null
                ? entity.getPrice().toString()
                : null;
            case "capacity" -> entity.getCapacity() != null
                ? entity.getCapacity().toString()
                : null;
            case "startsAt" -> entity.getStartsAt() != null
                ? entity.getStartsAt().toString()
                : null;
            default -> entity.getCreatedAt() != null
                ? entity.getCreatedAt().toString()
                : null;
        };
        String secondaryVal =
            entity.getCreatedAt() != null
                ? entity.getCreatedAt().toString()
                : null;

        KeysetCursor cursor = new KeysetCursor(
            entity.getId(),
            sortField,
            primaryVal,
            secondaryVal
        );
        return KeysetCursorCodec.encode(cursor);
    }
}
