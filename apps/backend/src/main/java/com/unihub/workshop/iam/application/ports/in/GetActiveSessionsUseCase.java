package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.CursorPage;

import java.util.UUID;

public interface GetActiveSessionsUseCase {
    CursorPage<ActiveSession> getActiveSessions(UUID userId, UUID currentSessionId, String cursor, Integer size);
}
