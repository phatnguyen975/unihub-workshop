package com.unihub.workshop.shared.realtime.infrastructure;

import com.unihub.workshop.shared.realtime.application.ports.out.EmitterRegistryPort;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Thread-safe in-memory registry maintaining active SseEmitter connections
 * indexed by authenticated user ID. Implements EmitterRegistryPort.
 */
@Component
public class SseEmitterRegistry implements EmitterRegistryPort {

    private final Map<UUID, Set<SseEmitter>> userEmitters =
        new ConcurrentHashMap<>();
    private final AtomicInteger activeConnectionCount = new AtomicInteger(0);

    @Override
    public void register(UUID userId, SseEmitter emitter) {
        if (userId == null || emitter == null) {
            return;
        }
        userEmitters
            .computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>())
            .add(emitter);
        activeConnectionCount.incrementAndGet();
    }

    @Override
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

    @Override
    public Set<SseEmitter> getEmitters(UUID userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<SseEmitter> emitters = userEmitters.get(userId);
        return emitters != null
            ? Collections.unmodifiableSet(emitters)
            : Collections.emptySet();
    }

    @Override
    public Set<UUID> getConnectedUserIds() {
        return Collections.unmodifiableSet(userEmitters.keySet());
    }

    @Override
    public List<SseEmitter> getAllEmitters() {
        List<SseEmitter> result = new ArrayList<>();
        for (Set<SseEmitter> emitters : userEmitters.values()) {
            result.addAll(emitters);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
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

    @Override
    public int getActiveConnectionCount() {
        return Math.max(0, activeConnectionCount.get());
    }

    @Override
    public int getConnectionCount(UUID userId) {
        if (userId == null) {
            return 0;
        }
        Set<SseEmitter> emitters = userEmitters.get(userId);
        return emitters != null ? emitters.size() : 0;
    }
}
