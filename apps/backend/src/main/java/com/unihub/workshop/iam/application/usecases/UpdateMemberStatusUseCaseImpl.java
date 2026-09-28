package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.UpdateMemberStatusCommand;
import com.unihub.workshop.iam.application.ports.in.UpdateMemberStatusUseCase;
import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.iam.application.ports.out.RefreshTokenRepository;
import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.shared.events.UserStatusChangedEvent;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateMemberStatusUseCaseImpl
    implements UpdateMemberStatusUseCase
{

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JtiBlacklistPort jtiBlacklistPort;
    private final SessionTrackerPort sessionTrackerPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public User execute(UpdateMemberStatusCommand command) {
        if (
            command == null ||
            command.organizationId() == null ||
            command.callerId() == null ||
            command.targetUserId() == null
        ) {
            throw new BadRequestException(
                "Organization ID, caller ID, and target user ID must not be null"
            );
        }

        // 1. Check self-status modification forbidden
        if (command.callerId().equals(command.targetUserId())) {
            throw new ForbiddenException(
                "urn:unihub:error:self-status-modification-forbidden",
                "Organizers cannot modify their own active status."
            );
        }

        // 2. Find target member in caller organization
        User targetUser = userRepository
            .findByIdAndOrganizationId(
                command.targetUserId(),
                command.organizationId()
            )
            .orElseThrow(() ->
                new NotFoundException("Member not found in your organization.")
            );

        // 3. Idempotent: if status is already the requested value, return immediately
        if (targetUser.isActive() == command.isActive()) {
            return targetUser;
        }

        Instant now = Instant.now();

        // 4. Ban flow: Redis fail-closed session invalidation
        if (!command.isActive()) {
            List<RefreshToken> activeSessions =
                refreshTokenRepository.findAllActiveSessionsForUser(
                    command.targetUserId(),
                    now
                );

            try {
                for (RefreshToken session : activeSessions) {
                    Optional<String> remoteJti =
                        sessionTrackerPort.getActiveJti(session.id());
                    if (remoteJti.isPresent() && !remoteJti.get().isBlank()) {
                        Duration remainingTtl =
                            sessionTrackerPort.getRemainingTtl(session.id());
                        if (remainingTtl == null || remainingTtl.isNegative()) {
                            remainingTtl = Duration.ZERO;
                        }
                        jtiBlacklistPort.blacklistJti(
                            remoteJti.get(),
                            remainingTtl
                        );
                    }
                    sessionTrackerPort.removeSession(session.id());
                }
            } catch (Exception e) {
                throw new ServiceUnavailableException(
                    "Session revocation service is temporarily unavailable. Member status was not changed.",
                    30
                );
            }

            // Revoke active refresh tokens in PostgreSQL
            refreshTokenRepository.revokeAllActiveTokensForUser(
                command.targetUserId(),
                now
            );
        }

        // 5. Update user is_active status in PostgreSQL
        userRepository.updateIsActive(
            command.targetUserId(),
            command.organizationId(),
            command.isActive()
        );

        // 6. Publish outbox notification event
        eventPublisher.publishEvent(
            new UserStatusChangedEvent(
                command.targetUserId(),
                command.organizationId(),
                targetUser.email(),
                targetUser.fullName(),
                command.isActive(),
                now
            )
        );

        log.info(
            "Updated active status for member {} in organization {} to isActive={} by organizer {}",
            command.targetUserId(),
            command.organizationId(),
            command.isActive(),
            command.callerId()
        );

        return targetUser.updateActiveStatus(
            command.isActive(),
            command.callerId()
        );
    }
}
