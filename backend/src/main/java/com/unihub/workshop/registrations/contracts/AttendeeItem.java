package com.unihub.workshop.registrations.contracts;

/**
 * Simple DTO representing a registered attendee.
 */
public record AttendeeItem(
        String id,
        String studentId,
        String fullName,
        String email,
        String status) {
}
