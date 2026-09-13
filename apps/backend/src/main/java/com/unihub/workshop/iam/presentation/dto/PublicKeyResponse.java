package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.shared.security.PublicKeyService;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicKeyResponse(
    String keyId,
    String publicKeyPem,
    Instant issuedAt
) {
    public static PublicKeyResponse from(PublicKeyService service) {
        if (service == null) {
            return null;
        }
        return new PublicKeyResponse(
            service.getKeyId(),
            service.getPublicKeyPem(),
            service.getIssuedAt()
        );
    }
}
