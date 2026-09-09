package com.unihub.workshop.shared.realtime.application;

import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.realtime.application.ports.out.UserStatusPort;
import com.unihub.workshop.shared.realtime.domain.SseEmitterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final long streamTimeoutMs;

    public StreamSubscriptionService(
            SseEmitterRegistry registry,
            @Autowired(required = false) UserStatusPort userStatusPort,
            @Value("${app.realtime.stream-timeout-ms:1800000}") long streamTimeoutMs) {
        this.registry = registry;
        this.userStatusPort = userStatusPort;
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

        emitter.onCompletion(() -> {
            log.debug("SSE stream completed for user: {}", userId);
            registry.remove(userId, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE stream timed out for user: {}", userId);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            registry.remove(userId, emitter);
        });

        emitter.onError(throwable -> {
            log.debug("SSE stream error for user: {}, error: {}", userId, throwable.getMessage());
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            registry.remove(userId, emitter);
        });

        registry.register(userId, emitter);
        log.info("Established active SSE stream for user: {}, role: {}, active connections: {}",
                userId, role, registry.getActiveConnectionCount());

        return emitter;
    }
}
