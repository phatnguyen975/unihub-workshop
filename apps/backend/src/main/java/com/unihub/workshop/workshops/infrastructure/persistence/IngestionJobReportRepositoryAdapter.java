package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.JpaKeysetPagination;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.IngestionJobReportPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IngestionJobReportRepositoryAdapter
    implements IngestionJobReportRepository
{

    private final IngestionJobReportJpaRepository repository;
    private final IngestionJobReportPersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<IngestionJobReport> findByOrganizationId(
        UUID organizationId,
        String cursor,
        int size
    ) {
        KeysetCursor cursorData = KeysetCursorCodec.decode(
            cursor,
            "completedAt",
            null
        );
        if (cursorData != null && cursorData.asInstant() == null) {
            throw new BadRequestException("Invalid cursor format");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<IngestionJobReportJpaEntity> cq = cb.createQuery(
            IngestionJobReportJpaEntity.class
        );
        Root<IngestionJobReportJpaEntity> root = cq.from(
            IngestionJobReportJpaEntity.class
        );

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("organizationId"), organizationId));

        Path<Instant> completedAtPath = root.get("completedAt");
        Path<UUID> idPath = root.get("id");

        if (cursorData != null) {
            predicates.add(
                JpaKeysetPagination.buildKeysetPredicate(
                    cb,
                    completedAtPath,
                    cursorData.asInstant(),
                    true,
                    completedAtPath,
                    cursorData.asInstant(),
                    idPath,
                    cursorData.id()
                )
            );
        }

        cq.where(predicates.toArray(new Predicate[0]));
        JpaKeysetPagination.applyOrders(
            cb,
            cq,
            completedAtPath,
            true,
            null,
            idPath
        );

        JpaKeysetPagination.KeysetSlice<IngestionJobReportJpaEntity> slice =
            JpaKeysetPagination.execute(entityManager, cq, size);

        List<IngestionJobReport> items = mapper.toDomainList(slice.items());

        String nextCursor = null;
        if (slice.hasMore() && !slice.items().isEmpty()) {
            IngestionJobReportJpaEntity lastItem = slice
                .items()
                .get(slice.items().size() - 1);
            nextCursor = KeysetCursorCodec.encode(
                new KeysetCursor(
                    lastItem.getId(),
                    "completedAt",
                    lastItem.getCompletedAt().toString(),
                    null,
                    null
                )
            );
        }

        return new CursorPage<>(items, nextCursor, slice.hasMore());
    }

    @Override
    @Transactional
    public Optional<IngestionJobReport> findByIdAndOrganizationIdFromPrimary(
        UUID id,
        UUID organizationId
    ) {
        return repository
            .findByIdAndOrganizationId(id, organizationId)
            .map(mapper::toDomain);
    }
}
