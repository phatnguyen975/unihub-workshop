package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserVerificationTokenPersistenceMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserVerificationTokenRepositoryAdapter
    implements UserVerificationTokenRepository
{

    private final UserVerificationTokenJpaRepository repository;
    private final UserVerificationTokenPersistenceMapper tokenMapper;

    @Override
    public UserVerificationToken save(UserVerificationToken token) {
        UserVerificationTokenJpaEntity entity = tokenMapper.toEntity(token);
        UserVerificationTokenJpaEntity saved = repository.save(entity);
        return tokenMapper.toDomain(saved);
    }

    @Override
    public Optional<UserVerificationToken> findLatestByUserIdAndType(
        UUID userId,
        String tokenType
    ) {
        return repository
            .findFirstByUserIdAndTokenTypeOrderByCreatedAtDesc(
                userId,
                tokenType
            )
            .map(tokenMapper::toDomain);
    }

    @Override
    public boolean markConsumed(UUID tokenId) {
        return repository.markConsumed(tokenId, Instant.now()) > 0;
    }

    @Override
    public void invalidateActiveTokens(UUID userId, String tokenType) {
        repository.invalidateActiveTokens(userId, tokenType, Instant.now());
    }
}
