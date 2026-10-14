package com.unihub.workshop.registrations.domain.model;

import java.time.Instant;

/**
 * Cryptographic ticket value object representing offline check-in credentials.
 *
 * @param qrPayload canonical UTF-8 JSON payload
 * @param qrSignature Base64 RSA-SHA256 signature
 * @param qrIssuedAt issuance timestamp matching issuedAt in payload
 */
public record QrTicket(
    String qrPayload,
    String qrSignature,
    Instant qrIssuedAt
) {}
