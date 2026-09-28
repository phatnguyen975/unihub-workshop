package com.unihub.workshop.workshops.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.unihub.workshop.shared.exception.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkshopTest {

    @Test
    @DisplayName(
        "createDraft sets default values and initializes status to DRAFT with version 0"
    )
    void shouldCreateDraftWithDefaults() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Workshop draft = Workshop.createDraft(
            orgId,
            "Clean Architecture Workshop",
            "clean-architecture-workshop-abc123",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            userId
        );

        assertThat(draft.id()).isNotNull();
        assertThat(draft.organizationId()).isEqualTo(orgId);
        assertThat(draft.title()).isEqualTo("Clean Architecture Workshop");
        assertThat(draft.slug()).isEqualTo(
            "clean-architecture-workshop-abc123"
        );
        assertThat(draft.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(draft.price()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(draft.currency()).isEqualTo("USD");
        assertThat(draft.status()).isEqualTo(WorkshopStatus.DRAFT);
        assertThat(draft.version()).isEqualTo(0);
        assertThat(draft.createdAt()).isNotNull();
        assertThat(draft.updatedAt()).isNotNull();
        assertThat(draft.createdBy()).isEqualTo(userId);
        assertThat(draft.updatedBy()).isEqualTo(userId);
    }

    @Test
    @DisplayName(
        "createDraft retains custom timezone, price, and currency when provided"
    )
    void shouldCreateDraftWithCustomValues() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal price = new BigDecimal("99.99");

        Workshop draft = Workshop.createDraft(
            orgId,
            "Advanced Java",
            "advanced-java-xyz789",
            "Description",
            "https://example.com/cover.png",
            "Room 101",
            "UTC",
            Instant.parse("2026-10-01T10:00:00Z"),
            Instant.parse("2026-10-01T12:00:00Z"),
            50,
            price,
            "EUR",
            userId
        );

        assertThat(draft.timezone()).isEqualTo("UTC");
        assertThat(draft.price()).isEqualTo(price);
        assertThat(draft.currency()).isEqualTo("EUR");
        assertThat(draft.capacity()).isEqualTo(50);
        assertThat(draft.description()).isEqualTo("Description");
        assertThat(draft.coverImageUrl()).isEqualTo(
            "https://example.com/cover.png"
        );
        assertThat(draft.location()).isEqualTo("Room 101");
        assertThat(draft.startsAt()).isEqualTo(
            Instant.parse("2026-10-01T10:00:00Z")
        );
        assertThat(draft.endsAt()).isEqualTo(
            Instant.parse("2026-10-01T12:00:00Z")
        );
    }

    @Test
    @DisplayName("updateDraft updates fields and sets updatedAt and updatedBy")
    void shouldUpdateDraftSuccessfully() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID updaterId = UUID.randomUUID();

        Workshop draft = Workshop.createDraft(
            orgId,
            "Old Title",
            "old-title-123",
            "Old Desc",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            userId
        );

        Workshop updated = draft.updateDraft(
            "New Title",
            "new-title-456",
            "New Desc",
            "https://example.com/new.png",
            "Room 202",
            "UTC",
            Instant.parse("2026-11-01T09:00:00Z"),
            Instant.parse("2026-11-01T11:00:00Z"),
            100,
            new BigDecimal("29.99"),
            "VND",
            updaterId
        );

        assertThat(updated.id()).isEqualTo(draft.id());
        assertThat(updated.organizationId()).isEqualTo(orgId);
        assertThat(updated.title()).isEqualTo("New Title");
        assertThat(updated.slug()).isEqualTo("new-title-456");
        assertThat(updated.description()).isEqualTo("New Desc");
        assertThat(updated.coverImageUrl()).isEqualTo(
            "https://example.com/new.png"
        );
        assertThat(updated.location()).isEqualTo("Room 202");
        assertThat(updated.timezone()).isEqualTo("UTC");
        assertThat(updated.startsAt()).isEqualTo(
            Instant.parse("2026-11-01T09:00:00Z")
        );
        assertThat(updated.endsAt()).isEqualTo(
            Instant.parse("2026-11-01T11:00:00Z")
        );
        assertThat(updated.capacity()).isEqualTo(100);
        assertThat(updated.price()).isEqualByComparingTo(
            new BigDecimal("29.99")
        );
        assertThat(updated.currency()).isEqualTo("VND");
        assertThat(updated.status()).isEqualTo(WorkshopStatus.DRAFT);
        assertThat(updated.version()).isEqualTo(0);
        assertThat(updated.createdBy()).isEqualTo(userId);
        assertThat(updated.updatedBy()).isEqualTo(updaterId);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(draft.updatedAt());
    }

    @Test
    @DisplayName(
        "updateDraft throws ConflictException when status is not DRAFT"
    )
    void shouldThrowConflictExceptionWhenUpdatingNonDraftWorkshop() {
        Workshop published = Workshop.builder()
            .id(UUID.randomUUID())
            .organizationId(UUID.randomUUID())
            .title("Published Workshop")
            .slug("published-workshop")
            .status(WorkshopStatus.PUBLISHED)
            .version(1)
            .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            published.updateDraft(
                "New Title",
                "new-title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID()
            )
        )
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> {
                ConflictException ce = (ConflictException) e;
                org.assertj.core.api.Assertions.assertThat(
                    ce.getType()
                ).isEqualTo("urn:unihub:error:conflict");
            });
    }

    @Test
    @DisplayName(
        "createDraft attaches speakers and updateDraft replaces or retains speakers"
    )
    void shouldHandleSpeakersOnCreateAndUpdate() {
        UUID workshopId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Speaker s1 = Speaker.create(
            workshopId,
            "Alice",
            "Alice@Example.com",
            "Bio",
            null,
            null,
            0,
            userId
        );

        Workshop draft = Workshop.createDraft(
            UUID.randomUUID(),
            "Title",
            "title-slug",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            userId,
            java.util.List.of(s1)
        );

        assertThat(draft.speakers()).hasSize(1);
        assertThat(draft.speakers().get(0).name()).isEqualTo("Alice");
        assertThat(draft.speakers().get(0).email()).isEqualTo(
            "alice@example.com"
        );

        // When updating with null speakers -> retains existing speakers
        Workshop updatedRetain = draft.updateDraft(
            "New Title",
            "new-title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            userId,
            null
        );
        assertThat(updatedRetain.speakers()).hasSize(1);

        // When updating with new list -> replaces speakers
        Speaker s2 = Speaker.create(
            workshopId,
            "Bob",
            "bob@example.com",
            null,
            null,
            null,
            0,
            userId
        );
        Workshop updatedReplace = draft.updateDraft(
            "New Title",
            "new-title",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            userId,
            java.util.List.of(s2)
        );
        assertThat(updatedReplace.speakers()).hasSize(1);
        assertThat(updatedReplace.speakers().get(0).name()).isEqualTo("Bob");
    }
}
