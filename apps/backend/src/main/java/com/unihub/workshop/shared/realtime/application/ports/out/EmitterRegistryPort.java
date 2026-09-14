package com.unihub.workshop.shared.realtime.application.ports.out;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Outbound port for managing in-memory active Server-Sent Events (SSE) connections.
 */
public interface EmitterRegistryPort {
    /**
     * Registers a new SseEmitter for the given user.
     */
    void register(UUID userId, SseEmitter emitter);

    /**
     * Removes an SseEmitter for the given user.
     */
    void remove(UUID userId, SseEmitter emitter);

    /**
     * Retrieves all active emitters registered for the given user.
     */
    Set<SseEmitter> getEmitters(UUID userId);

    /**
     * Retrieves all user IDs with at least one active connection on this node.
     */
    Set<UUID> getConnectedUserIds();

    /**
     * Retrieves a snapshot of all active emitters across all users.
     */
    List<SseEmitter> getAllEmitters();

    /**
     * Executes the given action for each registered emitter with its owning user ID.
     */
    void forEachEmitter(BiConsumer<UUID, SseEmitter> consumer);

    /**
     * Returns the total count of active SSE connections currently open.
     */
    int getActiveConnectionCount();

    /**
     * Returns the number of active connections for a specific user.
     */
    int getConnectionCount(UUID userId);
}
