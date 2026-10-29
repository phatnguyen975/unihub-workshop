package com.unihub.workshop.workshops.domain.model;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@link StaffAssignedWorkshop} domain invariants.
 *
 * <p>SF-02 regression: workshops.location is NOT NULL in the DB (V3 migration);
 * the domain record must reject a null location so that a misconfigured data row
 * never silently surfaces as a 500 error through the stack.
 */
class StaffAssignedWorkshopTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-15T12:00:00Z");
    private static final Instant ASSIGNED = Instant.parse(
        "2026-11-01T10:00:00Z"
    );

    @Test
    @DisplayName(
        "Should construct valid StaffAssignedWorkshop when all required fields are present"
    )
    void shouldConstruct_whenAllRequiredFieldsPresent() {
        assertThatNoException().isThrownBy(() ->
            new StaffAssignedWorkshop(
                ID,
                "Python Bootcamp",
                "python-bootcamp",
                "A description",
                "https://img.png",
                "Room 101",
                NOW,
                LATER,
                WorkshopStatus.PUBLISHED,
                "Faculty of CS",
                ASSIGNED
            )
        );
    }

    @Test
    @DisplayName(
        "Should allow null description and coverImageUrl (nullable fields)"
    )
    void shouldAllowNullOptionalFields() {
        assertThatNoException().isThrownBy(() ->
            new StaffAssignedWorkshop(
                ID,
                "Python Bootcamp",
                "python-bootcamp",
                null, // description nullable
                null, // coverImageUrl nullable
                "Room 101",
                NOW,
                LATER,
                WorkshopStatus.PUBLISHED,
                "Faculty of CS",
                ASSIGNED
            )
        );
    }

    // --- SF-02 regression: location must not be null ---

    @Test
    @DisplayName(
        "[SF-02] Should reject null location — domain invariant must match DB NOT NULL constraint"
    )
    void shouldRejectNullLocation_sf02Regression() {
        assertThatThrownBy(() ->
            new StaffAssignedWorkshop(
                ID,
                "Python Bootcamp",
                "python-bootcamp",
                "A description",
                "https://img.png",
                null, // location = null — must be rejected
                NOW,
                LATER,
                WorkshopStatus.PUBLISHED,
                "Faculty of CS",
                ASSIGNED
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("location must not be null or blank");
    }

    @Test
    @DisplayName(
        "[SF-02] Should reject blank location — domain invariant must match DB NOT NULL constraint"
    )
    void shouldRejectBlankLocation_sf02Regression() {
        assertThatThrownBy(() ->
            new StaffAssignedWorkshop(
                ID,
                "Python Bootcamp",
                "python-bootcamp",
                "A description",
                "https://img.png",
                "   ", // blank location — must be rejected
                NOW,
                LATER,
                WorkshopStatus.PUBLISHED,
                "Faculty of CS",
                ASSIGNED
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("location must not be null or blank");
    }

    @Test
    @DisplayName("Should reject null id")
    void shouldRejectNullId() {
        assertThatThrownBy(() ->
            new StaffAssignedWorkshop(
                null,
                "Title",
                "slug",
                null,
                null,
                "Room 1",
                NOW,
                LATER,
                WorkshopStatus.PUBLISHED,
                "Org",
                ASSIGNED
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id must not be null");
    }

    @Test
    @DisplayName("Should reject null or blank title")
    void shouldRejectNullOrBlankTitle() {
        assertThatThrownBy(() ->
            new StaffAssignedWorkshop(
                ID,
                null,
                "slug",
                null,
                null,
                "Room 1",
                NOW,
                LATER,
                WorkshopStatus.PUBLISHED,
                "Org",
                ASSIGNED
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title must not be null or blank");

        assertThatThrownBy(() ->
            new StaffAssignedWorkshop(
                ID,
                "  ",
                "slug",
                null,
                null,
                "Room 1",
                NOW,
                LATER,
                WorkshopStatus.PUBLISHED,
                "Org",
                ASSIGNED
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title must not be null or blank");
    }

    @Test
    @DisplayName("Should reject null status")
    void shouldRejectNullStatus() {
        assertThatThrownBy(() ->
            new StaffAssignedWorkshop(
                ID,
                "Title",
                "slug",
                null,
                null,
                "Room 1",
                NOW,
                LATER,
                null,
                "Org",
                ASSIGNED
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status must not be null");
    }
}
