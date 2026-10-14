package com.unihub.workshop.registrations.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Domain read-model projection representing full registration details enriched with workshop metadata and conditional QR credentials.
 *
 * @param id registration ID
 * @param workshopId target workshop ID
 * @param workshopTitle workshop display title
 * @param workshopStartsAt workshop scheduled start time
 * @param workshopEndsAt workshop scheduled end time
 * @param workshopLocation workshop venue or room name
 * @param studentId registered student user ID
 * @param status registration status
 * @param createdAt registration creation timestamp
 * @param qrPayload canonical JSON QR payload (only present when CONFIRMED)
 * @param qrSignature Base64 RSA-SHA256 signature (only present when CONFIRMED)
 * @param qrIssuedAt QR issuance timestamp (only present when CONFIRMED)
 */
@Builder(toBuilder = true)
public record RegistrationDetail(
    UUID id,
    UUID workshopId,
    String workshopTitle,
    Instant workshopStartsAt,
    Instant workshopEndsAt,
    String workshopLocation,
    UUID studentId,
    RegistrationStatus status,
    Instant createdAt,
    String qrPayload,
    String qrSignature,
    Instant qrIssuedAt
) {}
