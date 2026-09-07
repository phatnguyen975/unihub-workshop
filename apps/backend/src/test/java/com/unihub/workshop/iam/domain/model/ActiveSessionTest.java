package com.unihub.workshop.iam.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveSessionTest {

    @Test
    void shouldCreateActiveSessionAndCopyFlag() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ActiveSession session = new ActiveSession(id, "Chrome 120", "127.0.0.1", now, false);

        assertThat(session.id()).isEqualTo(id);
        assertThat(session.deviceInfo()).isEqualTo("Chrome 120");
        assertThat(session.ipAddress()).isEqualTo("127.0.0.1");
        assertThat(session.createdAt()).isEqualTo(now);
        assertThat(session.isCurrentSession()).isFalse();

        ActiveSession updated = session.withCurrentSession(true);
        assertThat(updated.isCurrentSession()).isTrue();
        assertThat(updated.id()).isEqualTo(id);
        assertThat(updated.deviceInfo()).isEqualTo("Chrome 120");
        assertThat(updated.ipAddress()).isEqualTo("127.0.0.1");
        assertThat(updated.createdAt()).isEqualTo(now);
    }
}
