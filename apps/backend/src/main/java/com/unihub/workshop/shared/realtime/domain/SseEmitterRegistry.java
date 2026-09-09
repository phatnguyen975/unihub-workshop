package com.unihub.workshop.shared.realtime.domain;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Thread-safe in-memory registry maintaining active SseEmitter connections
 * indexed by authenticated user ID.
 */
@Component
public class SseEmitterRegistry {

    private final Map<UUID, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final AtomicInteger activeConnectionCount = new AtomicInteger(0);

    /**
     * Registers a new SseEmitter for the given user.
     * Supports multiple concurrent sessions (e.g. multiple tabs, web + mobile).
     */
    public void register(UUID userId, SseEmitter emitter) {
        if (userId == null || emitter == null) {
            return;
        }
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        activeConnectionCount.incrementAndGet();
    }

    /**
     * Removes an SseEmitter for the given user.
     * Automatically cleans up the user entry if no emitters remain.
     */
    public void remove(UUID userId, SseEmitter emitter) {
        if (userId == null || emitter == null) {
            return;
        }
        userEmitters.computeIfPresent(userId, (key, emitters) -> {
            if (emitters.remove(emitter)) {
                activeConnectionCount.decrementAndGet();
            }
            return emitters.isEmpty() ? null : emitters;
        });
    }

    /**
     * Retrieves all active emitters registered for the given user.
     */
    public Set<SseEmitter> getEmitters(UUID userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<SseEmitter> emitters = userEmitters.get(userId);
        return emitters != null ? Collections.unmodifiableSet(emitters) : Collections.emptySet();
    }

    /**
     * Retrieves all user IDs with at least one active connection on this node.
     */
    public Set<UUID> getConnectedUserIds() {
        return Collections.unmodifiableSet(userEmitters.keySet());
    }

    /**
     * Retrieves a snapshot of all active emitters across all users.
     */
    public List<SseEmitter> getAllEmitters() {
        List<SseEmitter> result = new ArrayList<>();
        for (Set<SseEmitter> emitters : userEmitters.values()) {
            result.addAll(emitters);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Executes the given action for each registered emitter with its owning user ID.
     */
    public void forEachEmitter(BiConsumer<UUID, SseEmitter> consumer) {
        if (consumer == null) {
            return;
        }
        userEmitters.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                consumer.accept(userId, emitter);
            }
        });
    }

    /**
     * Returns the total count of active SSE connections currently open.
     */
    public int getActiveConnectionCount() {
        return Math.max(0, activeConnectionCount.get());
    }

    /**
     * Returns the number of active connections for a specific user.
     */
    public int getConnectionCount(UUID userId) {
        if (userId == null) {
            return 0;
        }
        Set<SseEmitter> emitters = userEmitters.get(userId);
        return emitters != null ? emitters.size() : 0;
    }
}
