package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.RevokeSessionUseCase;
import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RevokeSessionUseCaseImpl implements RevokeSessionUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JtiBlacklistPort jtiBlacklistPort;
    private final SessionTrackerPort sessionTrackerPort;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    // Enforce fail-closed: Redis operations must succeed before any DB mutation
    public void revokeSession(
        UUID userId,
        UUID sessionIdToRevoke,
        UUID currentSessionId,
        String currentJti,
        Instant currentTokenExpiresAt
    ) {
        if (userId == null || sessionIdToRevoke == null) {
            throw new BadRequestException(
                "User ID and session ID to revoke must not be null"
            );
        }

        Instant now = Instant.now();

        // 1. Verify session exists, belongs to caller, and is active
        refreshTokenRepository
            .findActiveSessionByIdAndUserId(sessionIdToRevoke, userId, now)
            .orElseThrow(() ->
                new NotFoundException("Session not found or already revoked")
            );

        boolean isCurrent =
            currentSessionId != null &&
            sessionIdToRevoke.equals(currentSessionId);

        // 2. Perform Redis operations BEFORE DB mutation
        if (isCurrent) {
            Duration remainingTtl =
                currentTokenExpiresAt != null
                    ? Duration.between(now, currentTokenExpiresAt)
                    : Duration.ZERO;
            if (remainingTtl.isNegative()) {
                remainingTtl = Duration.ZERO;
            }
            if (currentJti != null && !currentJti.isBlank()) {
                jtiBlacklistPort.blacklistJti(currentJti, remainingTtl);
            }
        } else {
            Optional<String> remoteJti = sessionTrackerPort.getActiveJti(
                sessionIdToRevoke
            );
            if (remoteJti.isPresent()) {
                Duration remainingTtl = sessionTrackerPort.getRemainingTtl(
                    sessionIdToRevoke
                );
                jtiBlacklistPort.blacklistJti(remoteJti.get(), remainingTtl);
            }
        }
        sessionTrackerPort.removeSession(sessionIdToRevoke);

        // 3. Mark session revoked in Primary DB
        refreshTokenRepository.revokeActiveSession(
            sessionIdToRevoke,
            userId,
            now
        );

        // 4. Record metric
        meterRegistry
            .counter(
                "auth.sessions.revoked",
                "outcome",
                "success",
                "target",
                isCurrent ? "current" : "single"
            )
            .increment();
    }
}
