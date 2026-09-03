package com.unihub.workshop.iam.presentation.dto;

import java.time.Instant;

public record PublicKeyResponse(
        String keyId,
        String publicKeyPem,
        Instant issuedAt) {
}
