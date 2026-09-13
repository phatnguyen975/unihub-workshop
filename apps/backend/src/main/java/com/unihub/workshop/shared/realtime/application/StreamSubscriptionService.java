package com.unihub.workshop.shared.realtime.application;

import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.realtime.application.ports.out.UserStatusPort;
import com.unihub.workshop.shared.realtime.config.RealtimeProperties;
import com.unihub.workshop.shared.realtime.domain.SseEmitterRegistry;
import com.unihub.workshop.shared.realtime.infrastructure.RealtimeMetrics;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Application service orchestrating the establishment and lifecycle management
 * of authenticated Server-Sent Events streams.
 */
@Service
@Slf4j
public class StreamSubscriptionService {

    private final SseEmitterRegistry registry;
    private final UserStatusPort userStatusPort;
    private final RealtimeMetrics realtimeMetrics;
    private final RealtimeProperties realtimeProperties;

    public StreamSubscriptionService(
        SseEmitterRegistry registry,
        UserStatusPort userStatusPort,
        RealtimeMetrics realtimeMetrics,
        RealtimeProperties realtimeProperties
    ) {
        this.registry = registry;
        this.userStatusPort = userStatusPort;
        this.realtimeMetrics = realtimeMetrics;
        this.realtimeProperties =
            realtimeProperties != null
                ? realtimeProperties
                : new RealtimeProperties(15, 1800000L);
    }

    /**
     * Subscribes an authenticated user to the real-time event stream.
     *
     * @param userId the authenticated user's ID
     * @param role   the user's role (STUDENT, ORGANIZER, STAFF)
     * @return an active SseEmitter instance with attached lifecycle callbacks
     * @throws ForbiddenException if the account is deactivated or banned
     */
    public SseEmitter subscribe(UUID userId, String role) {
        if (userStatusPort != null && !userStatusPort.isUserActive(userId)) {
            log.warn(
                "Rejected stream connection for inactive/banned user: {}",
                userId
            );
            throw new ForbiddenException(
                "urn:unihub:error:account-inactive",
                "User account is inactive or banned"
            );
        }

        SseEmitter emitter = new SseEmitter(
            realtimeProperties.streamTimeoutMs()
        );

        emitter.onCompletion(() ->
            handleDisconnect(userId, emitter, "client_abort")
        );

        emitter.onTimeout(() -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
            handleDisconnect(userId, emitter, "timeout");
        });

        emitter.onError(throwable -> {
            log.debug(
                "SSE stream error for user: {}, error: {}",
                userId,
                throwable.getMessage()
            );
            try {
                emitter.complete();
            } catch (Exception ignored) {}
            handleDisconnect(userId, emitter, "error");
        });

        registry.register(userId, emitter);
        log.info(
            "Established active SSE stream for user: {}, role: {}, active connections: {}",
            userId,
            role,
            registry.getActiveConnectionCount()
        );

        return emitter;
    }

    void handleDisconnect(UUID userId, SseEmitter emitter, String reason) {
        log.debug(
            "SSE stream disconnected for user: {}, reason: {}",
            userId,
            reason
        );
        registry.remove(userId, emitter);
        if (realtimeMetrics != null) {
            realtimeMetrics.incrementDisconnects(reason);
        }
    }
}
