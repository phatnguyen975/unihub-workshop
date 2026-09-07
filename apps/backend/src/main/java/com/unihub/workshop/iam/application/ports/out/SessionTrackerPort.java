package com.unihub.workshop.iam.application.ports.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface SessionTrackerPort {
    void trackSession(UUID sessionId, String jti, Duration ttl);

    Optional<String> getActiveJti(UUID sessionId);

    Duration getRemainingTtl(UUID sessionId);

    void removeSession(UUID sessionId);
}
