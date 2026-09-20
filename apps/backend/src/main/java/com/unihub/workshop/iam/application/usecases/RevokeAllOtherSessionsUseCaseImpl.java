package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.RevokeAllOtherSessionsUseCase;
import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.shared.exception.BadRequestException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RevokeAllOtherSessionsUseCaseImpl
    implements RevokeAllOtherSessionsUseCase
{

    private final RefreshTokenRepository refreshTokenRepository;
    private final JtiBlacklistPort jtiBlacklistPort;
    private final SessionTrackerPort sessionTrackerPort;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void revokeAllOtherSessions(UUID userId, UUID currentSessionId) {
        if (userId == null || currentSessionId == null) {
            throw new BadRequestException(
                "User ID and current session ID must not be null"
            );
        }

        Instant now = Instant.now();

        // 1. Find all active remote sessions for this user
        List<RefreshToken> remoteSessions =
            refreshTokenRepository.findActiveRemoteSessions(
                userId,
                currentSessionId,
                now
            );

        if (remoteSessions.isEmpty()) {
            return;
        }

        // 2. Perform Redis blacklist & session removal for each remote session BEFORE DB mutation
        for (RefreshToken session : remoteSessions) {
            Optional<String> remoteJti = sessionTrackerPort.getActiveJti(
                session.id()
            );
            if (remoteJti.isPresent() && !remoteJti.get().isBlank()) {
                Duration remainingTtl = sessionTrackerPort.getRemainingTtl(
                    session.id()
                );
                if (remainingTtl == null || remainingTtl.isNegative()) {
                    remainingTtl = Duration.ZERO;
                }
                jtiBlacklistPort.blacklistJti(remoteJti.get(), remainingTtl);
            }
            sessionTrackerPort.removeSession(session.id());
        }

        // 3. Atomically revoke all other sessions in Primary DB
        refreshTokenRepository.revokeAllOtherActiveSessions(
            userId,
            currentSessionId,
            now
        );
    }
}
