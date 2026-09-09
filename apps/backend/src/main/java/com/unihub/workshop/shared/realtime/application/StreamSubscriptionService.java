package com.unihub.workshop.shared.realtime.application;

import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.realtime.application.ports.out.UserStatusPort;
import com.unihub.workshop.shared.realtime.domain.SseEmitterRegistry;
import com.unihub.workshop.shared.realtime.infrastructure.RealtimeMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

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
    private final long streamTimeoutMs;

    public StreamSubscriptionService(
            SseEmitterRegistry registry,
            UserStatusPort userStatusPort,
            RealtimeMetrics realtimeMetrics,
            @Value("${app.realtime.stream-timeout-ms:1800000}") long streamTimeoutMs) {
        this.registry = registry;
        this.userStatusPort = userStatusPort;
        this.realtimeMetrics = realtimeMetrics;
        this.streamTimeoutMs = streamTimeoutMs;
    }

    /**
     * Subscribes an authenticated user to the real-time event stream.
     *
     * @param userId         the authenticated user's ID
     * @param role           the user's role (STUDENT, ORGANIZER, STAFF)
     * @param organizationId optional organization ID
     * @return an active SseEmitter instance with attached lifecycle callbacks
     * @throws ForbiddenException if the account is deactivated or banned
     */
    public SseEmitter subscribe(UUID userId, String role, String organizationId) {
        if (userStatusPort != null && !userStatusPort.isUserActive(userId)) {
            log.warn("Rejected stream connection for inactive/banned user: {}", userId);
            throw new ForbiddenException("urn:unihub:error:account-inactive", "User account is inactive or banned");
        }

        SseEmitter emitter = new SseEmitter(streamTimeoutMs);

        emitter.onCompletion(() -> handleDisconnect(userId, emitter, "client_abort"));

        emitter.onTimeout(() -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            handleDisconnect(userId, emitter, "timeout");
        });

        emitter.onError(throwable -> {
            log.debug("SSE stream error for user: {}, error: {}", userId, throwable.getMessage());
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            handleDisconnect(userId, emitter, "error");
        });

        registry.register(userId, emitter);
        log.info("Established active SSE stream for user: {}, role: {}, active connections: {}",
                userId, role, registry.getActiveConnectionCount());

        return emitter;
    }

    void handleDisconnect(UUID userId, SseEmitter emitter, String reason) {
        log.debug("SSE stream disconnected for user: {}, reason: {}", userId, reason);
        registry.remove(userId, emitter);
        if (realtimeMetrics != null) {
            realtimeMetrics.incrementDisconnects(reason);
        }
    }
}
