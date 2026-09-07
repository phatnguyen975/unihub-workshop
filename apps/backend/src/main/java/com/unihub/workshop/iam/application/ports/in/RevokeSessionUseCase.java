package com.unihub.workshop.iam.application.ports.in;

import java.time.Instant;
import java.util.UUID;

public interface RevokeSessionUseCase {
    void revokeSession(
            UUID userId,
            UUID sessionIdToRevoke,
            UUID currentSessionId,
            String currentJti,
            Instant currentTokenExpiresAt);
}
