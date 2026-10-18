package com.unihub.workshop.registrations.infrastructure.persistence;

import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.infrastructure.persistence.mapper.RegistrationPersistenceMapper;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.JpaKeysetPagination;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RegistrationRepositoryAdapter implements RegistrationRepository {

    private final RegistrationJpaRepository repository;
    private final RegistrationPersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Registration save(Registration registration) {
        RegistrationJpaEntity entity = mapper.toEntity(registration);
        RegistrationJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Registration> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveRegistration(
        UUID studentUserId,
        UUID workshopId
    ) {
        if (studentUserId == null || workshopId == null) {
            return false;
        }
        return repository.existsActiveRegistration(studentUserId, workshopId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOverlappingActiveRegistration(
        UUID studentUserId,
        Instant startsAt,
        Instant endsAt
    ) {
        if (studentUserId == null || startsAt == null || endsAt == null) {
            return false;
        }
        return repository.hasOverlappingActiveRegistration(
            studentUserId,
            startsAt,
            endsAt
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveRegistrations(UUID workshopId) {
        if (workshopId == null) {
            return 0L;
        }
        return repository.countActiveRegistrations(workshopId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<Registration> findByStudentUserId(
        UUID studentUserId,
        RegistrationStatus status,
        String cursor,
        int size
    ) {
        KeysetCursor cursorData = KeysetCursorCodec.decode(cursor, "createdAt");
        if (cursorData != null && cursorData.asInstant() == null) {
            throw new BadRequestException("Invalid cursor format");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<RegistrationJpaEntity> cq = cb.createQuery(
            RegistrationJpaEntity.class
        );
        Root<RegistrationJpaEntity> root = cq.from(RegistrationJpaEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("studentUserId"), studentUserId));
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status.name()));
        }

        Path<Instant> createdAtPath = root.get("createdAt");
        Path<UUID> idPath = root.get("id");

        if (cursorData != null) {
            predicates.add(
                JpaKeysetPagination.buildKeysetPredicate(
                    cb,
                    createdAtPath,
                    cursorData.asInstant(),
                    true,
                    createdAtPath,
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
            createdAtPath,
            true,
            null,
            idPath
        );

        int pageSize = Math.max(1, Math.min(size, 100));
        JpaKeysetPagination.KeysetSlice<RegistrationJpaEntity> slice =
            JpaKeysetPagination.execute(entityManager, cq, pageSize);

        List<Registration> items = slice
            .items()
            .stream()
            .map(mapper::toDomain)
            .toList();

        String nextCursor = null;
        if (slice.hasMore() && !slice.items().isEmpty()) {
            RegistrationJpaEntity lastItem = slice
                .items()
                .get(slice.items().size() - 1);
            nextCursor = KeysetCursorCodec.encode(
                KeysetCursor.of(
                    lastItem.getId(),
                    "createdAt",
                    lastItem.getCreatedAt().toString()
                )
            );
        }

        return new CursorPage<>(items, nextCursor, slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Registration> findExpiredPendingRegistrations(
        Instant now,
        int batchSize
    ) {
        Pageable pageable = PageRequest.of(0, Math.max(1, batchSize));
        return repository
            .findExpiredPendingRegistrations(now, pageable)
            .stream()
            .map(mapper::toDomain)
            .toList();
    }
}
