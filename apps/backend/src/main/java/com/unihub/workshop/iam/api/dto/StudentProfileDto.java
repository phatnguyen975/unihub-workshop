package com.unihub.workshop.iam.api.dto;

import java.util.UUID;

/**
 * Immutable DTO representing a student user profile for cross-module exchange.
 *
 * @param userId student user identifier
 * @param studentRecordId linked student directory record, or null when unlinked
 * @param fullName student full name
 * @param email student email address
 * @param studentNumber institution student number resolved from the student directory,
 *     or null when no directory record exists
 */
public record StudentProfileDto(
    UUID userId,
    UUID studentRecordId,
    String fullName,
    String email,
    String studentNumber
) {}
