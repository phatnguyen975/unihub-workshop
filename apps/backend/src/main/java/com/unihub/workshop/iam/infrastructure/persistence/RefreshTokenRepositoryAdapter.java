package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.RefreshTokenPersistenceMapper;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;
    private final RefreshTokenPersistenceMapper tokenMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void save(RefreshToken refreshToken) {
        RefreshTokenJpaEntity entity = tokenMapper.toEntity(refreshToken);
        repository.save(entity);
    }

    @Override
    @Transactional
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(tokenMapper::toDomain);
    }

    @Override
    @Transactional
    public void revokeAllActiveTokensForUser(UUID userId, Instant revokedAt) {
        repository.revokeAllActiveTokensForUser(userId, revokedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ActiveSession> findActiveSessions(
        UUID userId,
        String cursor,
        int size
    ) {
        KeysetCursor cursorData = KeysetCursorCodec.decode(
            cursor,
            "createdAt",
            null
        );
        if (cursorData != null && cursorData.asInstant() == null) {
            throw new BadRequestException("Invalid cursor format");
        }

        Instant now = Instant.now();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<RefreshTokenJpaEntity> cq = cb.createQuery(
            RefreshTokenJpaEntity.class
        );
        Root<RefreshTokenJpaEntity> root = cq.from(RefreshTokenJpaEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("userId"), userId));
        predicates.add(cb.isNull(root.get("revokedAt")));
        predicates.add(cb.greaterThan(root.get("expiresAt"), now));

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

        JpaKeysetPagination.KeysetSlice<RefreshTokenJpaEntity> slice =
            JpaKeysetPagination.execute(entityManager, cq, size);

        List<ActiveSession> items = tokenMapper.toActiveSessionList(
            slice.items()
        );

        String nextCursor = null;
        if (slice.hasMore() && !slice.items().isEmpty()) {
            RefreshTokenJpaEntity lastItem = slice
                .items()
                .get(slice.items().size() - 1);
            nextCursor = KeysetCursorCodec.encode(
                new KeysetCursor(
                    lastItem.getId(),
                    "createdAt",
                    lastItem.getCreatedAt().toString(),
                    null,
                    null
                )
            );
        }

        return new CursorPage<>(items, nextCursor, slice.hasMore());
    }

    @Override
    @Transactional
    public Optional<RefreshToken> findActiveSessionByIdAndUserId(
        UUID id,
        UUID userId,
        Instant now
    ) {
        return repository
            .findActiveSessionByIdAndUserId(id, userId, now)
            .map(tokenMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean revokeActiveSession(
        UUID id,
        UUID userId,
        Instant revokedAt
    ) {
        return (
            repository.revokeActiveSession(id, userId, revokedAt, revokedAt) > 0
        );
    }

    @Override
    @Transactional
    public List<RefreshToken> findActiveRemoteSessions(
        UUID userId,
        UUID currentSessionId,
        Instant now
    ) {
        return repository
            .findActiveRemoteSessions(userId, currentSessionId, now)
            .stream()
            .map(tokenMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public int revokeAllOtherActiveSessions(
        UUID userId,
        UUID currentSessionId,
        Instant revokedAt
    ) {
        return repository.revokeAllOtherActiveSessions(
            userId,
            currentSessionId,
            revokedAt,
            revokedAt
        );
    }

    @Override
    @Transactional
    public List<RefreshToken> findAllActiveSessionsForUser(
        UUID userId,
        Instant now
    ) {
        return repository
            .findAllActiveSessionsForUser(userId, now)
            .stream()
            .map(tokenMapper::toDomain)
            .toList();
    }
}
