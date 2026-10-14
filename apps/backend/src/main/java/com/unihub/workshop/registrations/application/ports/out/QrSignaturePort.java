package com.unihub.workshop.registrations.application.ports.out;

import com.unihub.workshop.registrations.domain.model.QrTicket;
import java.time.Instant;
import java.util.UUID;

public interface QrSignaturePort {
    /**
     * Generates a signed QR ticket for an approved registration.
     *
     * @param registrationId registration ID
     * @param workshopId workshop ID
     * @param studentId student user ID
     * @param issuedAt issuance timestamp
     * @return signed QrTicket
     */
    QrTicket generateSignedTicket(
        UUID registrationId,
        UUID workshopId,
        UUID studentId,
        Instant issuedAt
    );

    /**
     * Verifies the authenticity of a signed ticket payload.
     *
     * @param payload canonical JSON payload
     * @param base64Signature Base64 RSA signature
     * @return true if valid, false otherwise
     */
    boolean verifySignature(String payload, String base64Signature);
}
