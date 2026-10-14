package com.unihub.workshop.shared.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.Externalized;

class WorkshopRegisteredEventTest {

    @Test
    @DisplayName(
        "WorkshopRegisteredEvent is annotated with @Externalized routing to unihub.events::workshop.registered"
    )
    void eventHasExternalizedAnnotation() {
        Externalized annotation = WorkshopRegisteredEvent.class.getAnnotation(
            Externalized.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(
            "unihub.events::workshop.registered"
        );
    }

    @Test
    @DisplayName(
        "WorkshopRegisteredEvent preserves all snapshot fields and defaults occurredAt"
    )
    void eventPreservesSnapshotFields() {
        UUID registrationId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(7200);
        Instant qrIssuedAt = Instant.now();

        WorkshopRegisteredEvent event = new WorkshopRegisteredEvent(
            registrationId,
            workshopId,
            studentId,
            "student@university.edu",
            "Student Name",
            "Advanced Java",
            "advanced-java",
            startsAt,
            endsAt,
            "Room 404",
            "{\"reg\":\"id\"}",
            "sig123",
            qrIssuedAt
        );

        assertThat(event.registrationId()).isEqualTo(registrationId);
        assertThat(event.workshopId()).isEqualTo(workshopId);
        assertThat(event.studentId()).isEqualTo(studentId);
        assertThat(event.studentEmail()).isEqualTo("student@university.edu");
        assertThat(event.studentName()).isEqualTo("Student Name");
        assertThat(event.workshopTitle()).isEqualTo("Advanced Java");
        assertThat(event.workshopSlug()).isEqualTo("advanced-java");
        assertThat(event.startsAt()).isEqualTo(startsAt);
        assertThat(event.endsAt()).isEqualTo(endsAt);
        assertThat(event.location()).isEqualTo("Room 404");
        assertThat(event.qrPayload()).isEqualTo("{\"reg\":\"id\"}");
        assertThat(event.qrSignature()).isEqualTo("sig123");
        assertThat(event.qrIssuedAt()).isEqualTo(qrIssuedAt);
        assertThat(event.occurredAt()).isNotNull();
    }
}
