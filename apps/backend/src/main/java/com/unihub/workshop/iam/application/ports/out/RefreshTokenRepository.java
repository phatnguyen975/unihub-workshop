package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.RefreshToken;

public interface RefreshTokenRepository {
    void save(RefreshToken refreshToken);
}
