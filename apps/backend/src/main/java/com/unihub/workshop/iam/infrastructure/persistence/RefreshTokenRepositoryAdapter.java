package com.unihub.workshop.iam.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.RefreshTokenPersistenceMapper;
import com.unihub.workshop.shared.exception.BadRequestException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;
    private final RefreshTokenPersistenceMapper tokenMapper;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void save(RefreshToken refreshToken) {
        RefreshTokenJpaEntity entity = tokenMapper.toEntity(refreshToken);
        repository.save(entity);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(tokenMapper::toDomain);
    }

    @Override
    public void revokeAllActiveTokensForUser(UUID userId, Instant revokedAt) {
        repository.revokeAllActiveTokensForUser(userId, revokedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ActiveSession> findActiveSessions(
        UUID userId,
        String cursor,
        int limit
    ) {
        Instant cursorTime = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(cursor));
                CursorData cursorData = objectMapper.readValue(
                    decoded,
                    CursorData.class
                );
                if (
                    cursorData == null ||
                    cursorData.createdAt() == null ||
                    cursorData.id() == null
                ) {
                    throw new BadRequestException("Invalid cursor format");
                }
                cursorTime = cursorData.createdAt();
                cursorId = cursorData.id();
            } catch (Exception e) {
                if (e instanceof BadRequestException) {
                    throw (BadRequestException) e;
                }
                throw new BadRequestException("Invalid cursor format");
            }
        }

        Instant now = Instant.now();
        List<RefreshTokenJpaEntity> entities = repository.findActiveSessions(
            userId,
            now,
            cursorTime,
            cursorId,
            PageRequest.of(0, limit + 1)
        );

        boolean hasMore = entities.size() > limit;
        List<RefreshTokenJpaEntity> pageEntities = hasMore
            ? entities.subList(0, limit)
            : entities;

        List<ActiveSession> items = tokenMapper.toActiveSessionList(
            pageEntities
        );

        String nextCursor = null;
        if (hasMore && !pageEntities.isEmpty()) {
            RefreshTokenJpaEntity lastItem = pageEntities.get(
                pageEntities.size() - 1
            );
            try {
                CursorData nextCursorData = new CursorData(
                    lastItem.getCreatedAt(),
                    lastItem.getId()
                );
                String json = objectMapper.writeValueAsString(nextCursorData);
                nextCursor = Base64.getEncoder().encodeToString(
                    json.getBytes()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode next cursor", e);
            }
        }

        return new CursorPage<>(items, nextCursor, hasMore);
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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

    private record CursorData(Instant createdAt, UUID id) {}
}
