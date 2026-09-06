package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserVerificationTokenRepositoryAdapter implements UserVerificationTokenRepository {

    private final UserVerificationTokenJpaRepository repository;

    public UserVerificationTokenRepositoryAdapter(UserVerificationTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserVerificationToken save(UserVerificationToken token) {
        UserVerificationTokenJpaEntity entity = toEntity(token);
        UserVerificationTokenJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<UserVerificationToken> findLatestByUserIdAndType(UUID userId, String tokenType) {
        return repository.findFirstByUserIdAndTokenTypeOrderByCreatedAtDesc(userId, tokenType)
                .map(this::toDomain);
    }

    @Override
    public boolean markConsumed(UUID tokenId) {
        return repository.markConsumed(tokenId, Instant.now()) > 0;
    }

    @Override
    public void invalidateActiveTokens(UUID userId, String tokenType) {
        repository.invalidateActiveTokens(userId, tokenType, Instant.now());
    }

    private UserVerificationToken toDomain(UserVerificationTokenJpaEntity entity) {
        return new UserVerificationToken(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenType(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy());
    }

    private UserVerificationTokenJpaEntity toEntity(UserVerificationToken token) {
        UserVerificationTokenJpaEntity entity = new UserVerificationTokenJpaEntity();
        entity.setId(token.id());
        entity.setUserId(token.userId());
        entity.setTokenType(token.tokenType());
        entity.setTokenHash(token.tokenHash());
        entity.setExpiresAt(token.expiresAt());
        entity.setUsedAt(token.usedAt());
        entity.setCreatedAt(token.createdAt());
        entity.setUpdatedAt(token.updatedAt());
        entity.setCreatedBy(token.createdBy());
        entity.setUpdatedBy(token.updatedBy());
        return entity;
    }
}
