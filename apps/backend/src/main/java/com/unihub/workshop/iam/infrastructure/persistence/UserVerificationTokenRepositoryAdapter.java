package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.UserVerificationTokenRepository;
import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserVerificationTokenPersistenceMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserVerificationTokenRepositoryAdapter
    implements UserVerificationTokenRepository
{

    private final UserVerificationTokenJpaRepository repository;
    private final UserVerificationTokenPersistenceMapper tokenMapper;

    @Override
    @Transactional
    public UserVerificationToken save(UserVerificationToken token) {
        UserVerificationTokenJpaEntity entity = tokenMapper.toEntity(token);
        UserVerificationTokenJpaEntity saved = repository.save(entity);
        return tokenMapper.toDomain(saved);
    }

    @Override
    @Transactional
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
    @Transactional
    public boolean markConsumed(UUID tokenId) {
        return repository.markConsumed(tokenId, Instant.now()) > 0;
    }

    @Override
    @Transactional
    public void invalidateActiveTokens(UUID userId, String tokenType) {
        repository.invalidateActiveTokens(userId, tokenType, Instant.now());
    }
}
