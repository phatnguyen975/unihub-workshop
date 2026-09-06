package com.unihub.workshop.shared.events;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserProvisionedEventTest {

    @Test
    void constructor_withConvenienceSignature_setsCurrentTimestamp() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant before = Instant.now().minusMillis(100);

        UserProvisionedEvent event = new UserProvisionedEvent(
                userId, orgId, "engineering", "admin@eng.unihub.edu", "Admin User", "ORGANIZER", "tempPass123");

        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.organizationId()).isEqualTo(orgId);
        assertThat(event.orgSlug()).isEqualTo("engineering");
        assertThat(event.email()).isEqualTo("admin@eng.unihub.edu");
        assertThat(event.fullName()).isEqualTo("Admin User");
        assertThat(event.role()).isEqualTo("ORGANIZER");
        assertThat(event.temporaryPassword()).isEqualTo("tempPass123");
        assertThat(event.occurredAt()).isAfterOrEqualTo(before);
    }

    @Test
    void annotation_hasExternalizedToNotificationsQueue() {
        Externalized annotation = UserProvisionedEvent.class.getAnnotation(Externalized.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("unihub.events::notifications");
    }
}
