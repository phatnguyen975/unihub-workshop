package com.unihub.workshop.shared.realtime.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;

/**
 * Scheduled worker broadcasting keep-alive heartbeat comments (: ping\n\n)
 * to all active SSE connections to prevent idle proxy/NAT timeouts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HeartbeatScheduler {

    private final SseEmitterRegistry registry;
    private final RealtimeMetrics realtimeMetrics;

    /**
     * Broadcasts a keep-alive ping comment to all registered active emitters.
     * Catches IOExceptions and evicts broken sockets safely without leaking
     * resources or polluting error logs.
     */
    @Scheduled(fixedDelayString = "${app.realtime.heartbeat-interval-seconds:15}", timeUnit = TimeUnit.SECONDS)
    public void sendHeartbeat() {
        int totalActive = registry.getActiveConnectionCount();
        if (totalActive == 0) {
            return;
        }

        log.trace("Broadcasting keep-alive heartbeat ping to {} active connections", totalActive);

        registry.forEachEmitter((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception ex) {
                log.debug("Heartbeat ping failed for user {}, evicting broken connection: {}", userId, ex.getMessage());
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
                registry.remove(userId, emitter);
                if (realtimeMetrics != null) {
                    realtimeMetrics.incrementDisconnects("error");
                }
            }
        });
    }
}
