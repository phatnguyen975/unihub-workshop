package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(RefreshToken refreshToken) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setId(refreshToken.id());
        entity.setUserId(refreshToken.userId());
        entity.setTokenHash(refreshToken.tokenHash());
        entity.setDeviceInfo(refreshToken.deviceInfo());
        entity.setIpAddress(refreshToken.ipAddress());
        entity.setExpiresAt(refreshToken.expiresAt());
        entity.setRevokedAt(refreshToken.revokedAt());

        repository.save(entity);
    }
}
