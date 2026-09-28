package com.unihub.workshop.workshops.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.pagination.CursorPage;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WorkshopRepositoryAdapter implements WorkshopRepository {

    private final WorkshopJpaRepository repository;
    private final WorkshopSpeakerJpaRepository speakerRepository;
    private final WorkshopPersistenceMapper mapper;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    @Transactional
    public Workshop save(Workshop workshop) {
        WorkshopJpaEntity entity = mapper.toEntity(workshop);
        try {
            WorkshopJpaEntity saved = repository.saveAndFlush(entity);

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

        AdminWorkshopCursor cursor = decodeCursor(query.cursor(), sortField);

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
        if (isDesc) {
            cq.orderBy(cb.desc(sortPath), cb.desc(root.get("id")));
        } else {
            cq.orderBy(cb.asc(sortPath), cb.asc(root.get("id")));
        }

        int pageSize = query.size() != null ? query.size() : 20;
        List<WorkshopJpaEntity> entities = entityManager
            .createQuery(cq)
            .setMaxResults(pageSize + 1)
            .getResultList();

        boolean hasMore = entities.size() > pageSize;
        List<WorkshopJpaEntity> pageEntities = hasMore
            ? entities.subList(0, pageSize)
            : entities;

        Map<UUID, Integer> speakerCounts = fetchSpeakerCounts(pageEntities);

        List<WorkshopSummary> items = pageEntities
            .stream()
            .map(entity -> toWorkshopSummary(entity, speakerCounts))
            .toList();

        String nextCursor = null;
        if (hasMore && !pageEntities.isEmpty()) {
            nextCursor = encodeCursor(
                pageEntities.get(pageEntities.size() - 1),
                sortField
            );
        }

        return new CursorPage<>(items, nextCursor, hasMore);
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
        AdminWorkshopCursor cursor
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
        AdminWorkshopCursor cursor
    ) {
        Path<UUID> idPath = root.get("id");
        UUID cursorId = cursor.id();
        return switch (sortField) {
            case "price" -> buildKeysetBranch(
                cb,
                root.get("price"),
                cursor.price(),
                idPath,
                cursorId,
                isDesc
            );
            case "capacity" -> buildKeysetBranch(
                cb,
                root.get("capacity"),
                cursor.capacity(),
                idPath,
                cursorId,
                isDesc
            );
            case "startsAt" -> buildKeysetBranch(
                cb,
                root.get("startsAt"),
                cursor.startsAt(),
                idPath,
                cursorId,
                isDesc
            );
            default -> buildKeysetBranch(
                cb,
                root.get("createdAt"),
                cursor.createdAt(),
                idPath,
                cursorId,
                isDesc
            );
        };
    }

    /**
     * Builds a keyset (seek) predicate for one sort field, eliminating the
     * duplicate if/else isDesc block that each switch case previously repeated.
     *
     * <p>For descending: {@code (field < val) OR (field = val AND id < cursorId)}
     * <p>For ascending:  {@code (field > val) OR (field = val AND id > cursorId)}
     */
    private <T extends Comparable<? super T>> Predicate buildKeysetBranch(
        CriteriaBuilder cb,
        Path<T> fieldPath,
        T cursorVal,
        Path<UUID> idPath,
        UUID cursorId,
        boolean isDesc
    ) {
        if (isDesc) {
            return cb.or(
                cb.lessThan(fieldPath, cursorVal),
                cb.and(
                    cb.equal(fieldPath, cursorVal),
                    cb.lessThan(idPath, cursorId)
                )
            );
        } else {
            return cb.or(
                cb.greaterThan(fieldPath, cursorVal),
                cb.and(
                    cb.equal(fieldPath, cursorVal),
                    cb.greaterThan(idPath, cursorId)
                )
            );
        }
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

    /**
     * Encodes the last item of the current page into a Base64 JSON cursor.
     * The {@code sortField} is embedded in the token so that {@link #decodeCursor}
     * can detect cursor/sort mismatches and emit a specific error message.
     */
    private String encodeCursor(WorkshopJpaEntity entity, String sortField) {
        AdminWorkshopCursor cursor = switch (sortField) {
            case "price" -> new AdminWorkshopCursor(
                entity.getId(),
                sortField,
                null,
                null,
                entity.getPrice(),
                null
            );
            case "capacity" -> new AdminWorkshopCursor(
                entity.getId(),
                sortField,
                null,
                null,
                null,
                entity.getCapacity()
            );
            case "startsAt" -> new AdminWorkshopCursor(
                entity.getId(),
                sortField,
                null,
                entity.getStartsAt(),
                null,
                null
            );
            default -> new AdminWorkshopCursor(
                entity.getId(),
                sortField,
                entity.getCreatedAt(),
                null,
                null,
                null
            );
        };
        try {
            String json = objectMapper.writeValueAsString(cursor);
            return Base64.getEncoder().encodeToString(
                json.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            throw new InternalServerException(
                "Failed to encode next cursor",
                e
            );
        }
    }

    /**
     * Decodes a Base64 JSON cursor string.
     *
     * <ul>
     *   <li>Null/blank cursor → returns {@code null} (first-page request).</li>
     *   <li>Structurally corrupt → {@link BadRequestException} with "Invalid cursor format".</li>
     *   <li>Sort-field mismatch → {@link BadRequestException} with a clear mismatch message,
     *       so callers can distinguish it from a malformed token.</li>
     * </ul>
     */
    private AdminWorkshopCursor decodeCursor(
        String cursorStr,
        String sortField
    ) {
        if (cursorStr == null || cursorStr.isBlank()) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(cursorStr.trim());
            String json = new String(decodedBytes, StandardCharsets.UTF_8);
            AdminWorkshopCursor cursor = objectMapper.readValue(
                json,
                AdminWorkshopCursor.class
            );
            if (cursor == null || cursor.id() == null) {
                throw new BadRequestException("Invalid cursor format");
            }
            // If the token carries a sortField, use it for mismatch detection.
            if (
                cursor.sortField() != null &&
                !cursor.sortField().equals(sortField)
            ) {
                throw new BadRequestException(
                    "Cursor was issued for sort='" +
                        cursor.sortField() +
                        "' but the current request uses sort='" +
                        sortField +
                        "'. Discard the cursor and start from page 1."
                );
            }
            // Validate that the expected sort value is present in the token.
            switch (sortField) {
                case "price" -> {
                    if (cursor.price() == null) throw new BadRequestException(
                        "Invalid cursor format"
                    );
                }
                case "capacity" -> {
                    if (
                        cursor.capacity() == null
                    ) throw new BadRequestException("Invalid cursor format");
                }
                case "startsAt" -> {
                    if (
                        cursor.startsAt() == null
                    ) throw new BadRequestException("Invalid cursor format");
                }
                default -> {
                    if (
                        cursor.createdAt() == null
                    ) throw new BadRequestException("Invalid cursor format");
                }
            }
            return cursor;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Invalid cursor format");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record AdminWorkshopCursor(
        UUID id,
        String sortField,
        Instant createdAt,
        Instant startsAt,
        BigDecimal price,
        Integer capacity
    ) {}
}
