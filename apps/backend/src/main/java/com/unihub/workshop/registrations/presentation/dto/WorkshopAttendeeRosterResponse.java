package com.unihub.workshop.registrations.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.registrations.domain.model.WorkshopAttendeeRosterItem;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Presentation response envelope for workshop attendee roster endpoint.
 *
 * @param items list of roster entries on the current page
 * @param cursor cursor metadata for keyset pagination
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkshopAttendeeRosterResponse(
    List<AttendeeRosterItemResponse> items,
    CursorResponse cursor
) {
    /**
     * Single attendee roster entry response.
     *
     * @param id registration unique identifier
     * @param student nested student profile information
     * @param status registration lifecycle status
     * @param price price charged at registration time
     * @param currency ISO currency code
     * @param registeredAt registration creation timestamp
     */
    public record AttendeeRosterItemResponse(
        UUID id,
        StudentSummaryResponse student,
        String status,
        BigDecimal price,
        String currency,
        Instant registeredAt
    ) {}

    /**
     * Student summary profile information.
     *
     * @param id student user account identifier
     * @param fullName student full name
     * @param studentNumber student institutional registration number
     * @param email student institutional email address
     */
    public record StudentSummaryResponse(
        UUID id,
        String fullName,
        String studentNumber,
        String email
    ) {}

    /**
     * Keyset pagination cursor response.
     *
     * @param nextCursor opaque base64 cursor token, or null if no further pages
     * @param hasMore true if subsequent pages exist
     */
    public record CursorResponse(String nextCursor, boolean hasMore) {}

    /**
     * Factory method mapping from domain cursor page to presentation response.
     *
     * @param page domain CursorPage of WorkshopAttendeeRosterItem
     * @return WorkshopAttendeeRosterResponse
     */
    public static WorkshopAttendeeRosterResponse of(
        CursorPage<WorkshopAttendeeRosterItem> page
    ) {
        List<AttendeeRosterItemResponse> items = page
            .items()
            .stream()
            .map(item ->
                new AttendeeRosterItemResponse(
                    item.id(),
                    new StudentSummaryResponse(
                        item.student().id(),
                        item.student().fullName(),
                        item.student().studentNumber(),
                        item.student().email()
                    ),
                    item.status().name(),
                    item.price(),
                    item.currency(),
                    item.registeredAt()
                )
            )
            .toList();

        return new WorkshopAttendeeRosterResponse(
            items,
            new CursorResponse(page.nextCursor(), page.hasMore())
        );
    }
}
