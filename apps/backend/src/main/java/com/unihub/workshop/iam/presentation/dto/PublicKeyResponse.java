package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicKeyResponse(
    String keyId,
    String publicKeyPem,
    Instant issuedAt
) {}
