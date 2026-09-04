package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.PasswordResetTokenRepository;
import com.unihub.workshop.iam.domain.model.PasswordResetToken;
import org.springframework.stereotype.Component;

import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

@Component
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository repository;

    public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = toEntity(token);
        PasswordResetTokenJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<PasswordResetToken> findActiveTokenByUserEmail(String email) {
        return repository.findActiveTokensByEmail(email, Instant.now())
                .stream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public Optional<PasswordResetToken> findLatestByUserId(UUID userId) {
        return repository.findFirstByUserIdOrderByCreatedAtDesc(userId).map(this::toDomain);
    }

    @Override
    public void markConsumed(UUID tokenId) {
        repository.markConsumed(tokenId, Instant.now());
    }

    private PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
        return new PasswordResetToken(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy());
    }

    private PasswordResetTokenJpaEntity toEntity(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity();
        entity.setId(token.id());
        entity.setUserId(token.userId());
        entity.setTokenHash(token.tokenHash());
        entity.setExpiresAt(token.expiresAt());
        entity.setUsedAt(token.usedAt());
        entity.setCreatedAt(token.createdAt() != null ? token.createdAt() : Instant.now());
        entity.setUpdatedAt(token.updatedAt() != null ? token.updatedAt() : Instant.now());
        entity.setCreatedBy(token.createdBy());
        entity.setUpdatedBy(token.updatedBy());
        return entity;
    }
}
