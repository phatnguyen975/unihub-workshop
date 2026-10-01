package com.unihub.workshop.workshops.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    @Test
    @DisplayName(
        "publish transitions status from DRAFT to PUBLISHED and sets publishedAt"
    )
    void shouldPublishDraftSuccessfully() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID updaterId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        Instant startsAt = now.plusSeconds(86400 * 2); // 2 days in future
        Instant endsAt = startsAt.plusSeconds(7200); // + 2 hours

        Workshop draft = Workshop.createDraft(
            orgId,
            "Advanced Java",
            "advanced-java-1234",
            "Description",
            "https://example.com/cover.png",
            "Room 101",
            "UTC",
            startsAt,
            endsAt,
            50,
            BigDecimal.ZERO,
            "USD",
            userId
        );

        Workshop published = draft.publish(updaterId);

        assertThat(published.status()).isEqualTo(WorkshopStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.updatedAt()).isNotNull();
        assertThat(published.updatedBy()).isEqualTo(updaterId);
        assertThat(published.id()).isEqualTo(draft.id());
    }

    @Test
    @DisplayName(
        "publish throws ValidationException when current status is not DRAFT"
    )
    void shouldFailToPublishWhenStatusNotDraft() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop draft = Workshop.createDraft(
            orgId,
            "Advanced Java",
            "advanced-java-1234",
            "Description",
            "https://example.com/cover.png",
            "Room 101",
            "UTC",
            startsAt,
            endsAt,
            50,
            BigDecimal.ZERO,
            "USD",
            userId
        );
        Workshop published = draft.publish(userId);

        assertThatThrownBy(() -> published.publish(userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Only workshops in DRAFT status can be published"
            );
    }

    @Test
    @DisplayName(
        "publish throws ValidationException when mandatory fields are missing"
    )
    void shouldFailToPublishWhenMandatoryFieldsMissing() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);

        // Missing location, endsAt, capacity
        Workshop incompleteDraft = Workshop.createDraft(
            orgId,
            "Draft Title",
            "draft-title-1234",
            null,
            null,
            null, // location missing
            null,
            startsAt,
            null, // endsAt missing
            null, // capacity missing
            null,
            null,
            userId
        );

        assertThatThrownBy(() -> incompleteDraft.publish(userId))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> {
                ValidationException ve = (ValidationException) e;
                List<String> validationErrors = ve.getValidationErrors();
                assertThat(validationErrors).anyMatch(s ->
                    s.contains("location")
                );
                assertThat(validationErrors).anyMatch(s ->
                    s.contains("endsAt")
                );
            });
    }

    @Test
    @DisplayName(
        "publish throws ValidationException when startsAt is in the past or present"
    )
    void shouldFailToPublishWhenStartsAtNotInFuture() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().minusSeconds(3600); // 1 hour in the past
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop pastDraft = Workshop.createDraft(
            orgId,
            "Past Workshop",
            "past-workshop-1234",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            null,
            null,
            userId
        );

        assertThatThrownBy(() -> pastDraft.publish(userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("start time must be in the future");
    }

    @Test
    @DisplayName(
        "publish throws ValidationException when endsAt is not after startsAt"
    )
    void shouldFailToPublishWhenEndsAtBeforeOrEqualsStartsAt() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400);
        Instant endsAt = startsAt; // equals

        Workshop invalidDraft = Workshop.createDraft(
            orgId,
            "Workshop",
            "workshop-1234",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            null,
            null,
            userId
        );

        assertThatThrownBy(() -> invalidDraft.publish(userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("endsAt must be strictly after startsAt");
    }

    @Test
    @DisplayName(
        "updatePublished successfully updates presentation and capacity fields"
    )
    void shouldUpdatePublishedWorkshopSuccessfully() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID updaterId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop published = Workshop.createDraft(
            orgId,
            "Initial Title",
            "initial-title",
            "Initial Desc",
            "https://example.com/old.png",
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        ).publish(userId);

        Workshop updated = published.updatePublished(
            "New Title",
            "new-title",
            "New Description",
            "https://example.com/new.png",
            150,
            updaterId
        );

        assertThat(updated.title()).isEqualTo("New Title");
        assertThat(updated.slug()).isEqualTo("new-title");
        assertThat(updated.description()).isEqualTo("New Description");
        assertThat(updated.coverImageUrl()).isEqualTo(
            "https://example.com/new.png"
        );
        assertThat(updated.capacity()).isEqualTo(150);
        assertThat(updated.updatedBy()).isEqualTo(updaterId);
        assertThat(updated.updatedAt()).isNotNull();
        assertThat(updated.status()).isEqualTo(WorkshopStatus.PUBLISHED);
    }

    @Test
    @DisplayName(
        "updatePublished retains existing fields when optional fields are null"
    )
    void shouldRetainExistingValuesOnPartialUpdate() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID updaterId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop published = Workshop.createDraft(
            orgId,
            "Initial Title",
            "initial-title",
            "Initial Desc",
            "https://example.com/old.png",
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        ).publish(userId);

        Workshop updated = published.updatePublished(
            null, // retain title & slug
            null,
            null, // retain desc
            null, // retain coverImageUrl
            200, // update capacity
            updaterId
        );

        assertThat(updated.title()).isEqualTo("Initial Title");
        assertThat(updated.slug()).isEqualTo("initial-title");
        assertThat(updated.description()).isEqualTo("Initial Desc");
        assertThat(updated.coverImageUrl()).isEqualTo(
            "https://example.com/old.png"
        );
        assertThat(updated.capacity()).isEqualTo(200);
        assertThat(updated.updatedBy()).isEqualTo(updaterId);
    }

    @Test
    @DisplayName(
        "updatePublished throws ValidationException when workshop is in DRAFT status"
    )
    void shouldFailToUpdateWhenStatusIsDraft() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop draft = Workshop.createDraft(
            orgId,
            "Draft Title",
            "draft-title",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        );

        assertThatThrownBy(() ->
            draft.updatePublished(
                "New Title",
                "new-title",
                null,
                null,
                150,
                userId
            )
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Workshop is in DRAFT status");
    }

    @Test
    @DisplayName(
        "updatePublished throws ValidationException when title is blank or capacity is invalid"
    )
    void shouldFailToUpdateWhenFieldsInvalid() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop published = Workshop.createDraft(
            orgId,
            "Title",
            "title",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        ).publish(userId);

        assertThatThrownBy(() ->
            published.updatePublished("   ", "title", null, null, 150, userId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("title");

        assertThatThrownBy(() ->
            published.updatePublished("Valid", "valid", null, null, 0, userId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("capacity");
    }

    @Test
    @DisplayName(
        "reschedule updates startsAt, endsAt, updatedAt, updatedBy on PUBLISHED workshop"
    )
    void shouldReschedulePublishedWorkshop() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID updaterId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop published = Workshop.createDraft(
            orgId,
            "Title",
            "title",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        ).publish(userId);

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(10800);

        Workshop rescheduled = published.reschedule(
            newStartsAt,
            newEndsAt,
            updaterId
        );

        assertThat(rescheduled.startsAt()).isEqualTo(newStartsAt);
        assertThat(rescheduled.endsAt()).isEqualTo(newEndsAt);
        assertThat(rescheduled.updatedBy()).isEqualTo(updaterId);
        assertThat(rescheduled.updatedAt()).isNotNull();
        assertThat(rescheduled.status()).isEqualTo(WorkshopStatus.PUBLISHED);
    }

    @Test
    @DisplayName(
        "reschedule throws ValidationException when workshop is in DRAFT status"
    )
    void shouldFailToRescheduleWhenStatusIsDraft() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop draft = Workshop.createDraft(
            orgId,
            "Draft Title",
            "draft-title",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        );

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        assertThatThrownBy(() ->
            draft.reschedule(newStartsAt, newEndsAt, userId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Workshop is in DRAFT status");
    }

    @Test
    @DisplayName(
        "reschedule throws ValidationException when workshop is in terminal status"
    )
    void shouldFailToRescheduleWhenStatusIsTerminal() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop canceled = Workshop.createDraft(
            orgId,
            "Title",
            "title",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        )
            .publish(userId)
            .toBuilder()
            .status(WorkshopStatus.CANCELED)
            .build();

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        assertThatThrownBy(() ->
            canceled.reschedule(newStartsAt, newEndsAt, userId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("terminal status");
    }

    @Test
    @DisplayName(
        "reschedule throws ValidationException when startsAt is in the past"
    )
    void shouldFailToRescheduleWhenStartsAtInPast() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop published = Workshop.createDraft(
            orgId,
            "Title",
            "title",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        ).publish(userId);

        Instant pastStartsAt = Instant.now().minusSeconds(3600);
        Instant newEndsAt = Instant.now().plusSeconds(3600);

        assertThatThrownBy(() ->
            published.reschedule(pastStartsAt, newEndsAt, userId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("future")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "startsAt: Workshop start time must be in the future"
                );
            });
    }

    @Test
    @DisplayName(
        "reschedule throws ValidationException when endsAt is not strictly after startsAt"
    )
    void shouldFailToRescheduleWhenEndsAtNotAfterStartsAt() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop published = Workshop.createDraft(
            orgId,
            "Title",
            "title",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        ).publish(userId);

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 3);

        assertThatThrownBy(() ->
            published.reschedule(newStartsAt, newStartsAt, userId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("endsAt must be strictly after startsAt")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "endsAt: endsAt must be strictly after startsAt"
                );
            });

        assertThatThrownBy(() ->
            published.reschedule(
                newStartsAt,
                newStartsAt.minusSeconds(3600),
                userId
            )
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("endsAt must be strictly after startsAt")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "endsAt: endsAt must be strictly after startsAt"
                );
            });
    }

    @Test
    @DisplayName(
        "reschedule throws ValidationException when startsAt or endsAt is null"
    )
    void shouldFailToRescheduleWhenTimesNull() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop published = Workshop.createDraft(
            orgId,
            "Title",
            "title",
            null,
            null,
            "Auditorium A",
            "UTC",
            startsAt,
            endsAt,
            100,
            BigDecimal.ZERO,
            "USD",
            userId
        ).publish(userId);

        Instant future = Instant.now().plusSeconds(86400 * 3);

        assertThatThrownBy(() -> published.reschedule(null, future, userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must not be null")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "startsAt: must not be null"
                );
            });

        assertThatThrownBy(() -> published.reschedule(future, null, userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must not be null")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "endsAt: must not be null"
                );
            });
    }
}
