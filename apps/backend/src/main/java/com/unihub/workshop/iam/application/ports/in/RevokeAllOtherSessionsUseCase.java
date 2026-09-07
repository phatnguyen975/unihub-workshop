package com.unihub.workshop.iam.application.ports.in;

import java.util.UUID;

public interface RevokeAllOtherSessionsUseCase {
    void revokeAllOtherSessions(UUID userId, UUID currentSessionId);
}
