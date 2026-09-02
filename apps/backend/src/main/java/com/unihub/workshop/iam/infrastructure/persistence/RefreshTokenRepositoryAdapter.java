package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(RefreshToken refreshToken) {
        RefreshTokenJpaEntity entity = repository.findById(refreshToken.id()).orElseGet(RefreshTokenJpaEntity::new);
        entity.setId(refreshToken.id());
        entity.setUserId(refreshToken.userId());
        entity.setTokenHash(refreshToken.tokenHash());
        entity.setDeviceInfo(refreshToken.deviceInfo());
        entity.setIpAddress(refreshToken.ipAddress());
        entity.setExpiresAt(refreshToken.expiresAt());
        entity.setRevokedAt(refreshToken.revokedAt());

        repository.save(entity);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(this::mapToDomain);
    }

    private RefreshToken mapToDomain(RefreshTokenJpaEntity entity) {
        return new RefreshToken(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getDeviceInfo(),
                entity.getIpAddress(),
                entity.getExpiresAt(),
                entity.getRevokedAt());
    }
}
