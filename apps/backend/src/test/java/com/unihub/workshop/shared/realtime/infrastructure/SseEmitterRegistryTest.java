package com.unihub.workshop.shared.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    @DisplayName("should register and retrieve emitter for user")
    void shouldRegisterAndRetrieveEmitterForUser() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(60_000L);

        registry.register(userId, emitter);

        Set<SseEmitter> emitters = registry.getEmitters(userId);
        assertThat(emitters).containsExactly(emitter);
        assertThat(registry.getActiveConnectionCount()).isEqualTo(1);
        assertThat(registry.getConnectionCount(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "should support multiple concurrent emitters for same user (e.g. multiple tabs)"
    )
    void shouldSupportMultipleEmittersForSameUser() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter1 = new SseEmitter(60_000L);
        SseEmitter emitter2 = new SseEmitter(60_000L);

        registry.register(userId, emitter1);
        registry.register(userId, emitter2);

        Set<SseEmitter> emitters = registry.getEmitters(userId);
        assertThat(emitters).containsExactlyInAnyOrder(emitter1, emitter2);
        assertThat(registry.getActiveConnectionCount()).isEqualTo(2);
        assertThat(registry.getConnectionCount(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("should remove specific emitter and clean up empty user set")
    void shouldRemoveSpecificEmitterAndCleanUpEmptyUserSet() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter1 = new SseEmitter(60_000L);
        SseEmitter emitter2 = new SseEmitter(60_000L);

        registry.register(userId, emitter1);
        registry.register(userId, emitter2);

        registry.remove(userId, emitter1);

        assertThat(registry.getEmitters(userId)).containsExactly(emitter2);
        assertThat(registry.getActiveConnectionCount()).isEqualTo(1);

        registry.remove(userId, emitter2);

        assertThat(registry.getEmitters(userId)).isEmpty();
        assertThat(registry.getActiveConnectionCount()).isZero();
        assertThat(registry.getConnectionCount(userId)).isZero();
    }

    @Test
    @DisplayName("should return empty set when user has no active emitters")
    void shouldReturnEmptySetWhenUserHasNoEmitters() {
        UUID unknownUser = UUID.randomUUID();

        Set<SseEmitter> emitters = registry.getEmitters(unknownUser);

        assertThat(emitters).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("should retrieve all active emitters across all users")
    void shouldRetrieveAllActiveEmittersAcrossAllUsers() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        SseEmitter emitter1 = new SseEmitter(60_000L);
        SseEmitter emitter2 = new SseEmitter(60_000L);
        SseEmitter emitter3 = new SseEmitter(60_000L);

        registry.register(user1, emitter1);
        registry.register(user1, emitter2);
        registry.register(user2, emitter3);

        List<SseEmitter> allEmitters = registry.getAllEmitters();

        assertThat(allEmitters).containsExactlyInAnyOrder(
            emitter1,
            emitter2,
            emitter3
        );
        assertThat(registry.getActiveConnectionCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("should handle removing non-existent emitter gracefully")
    void shouldHandleRemovingNonExistentEmitterGracefully() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(60_000L);

        registry.remove(userId, emitter);

        assertThat(registry.getActiveConnectionCount()).isZero();
    }
}
