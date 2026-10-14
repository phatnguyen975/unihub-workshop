package com.unihub.workshop.registrations.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.registrations.application.ports.out.QrSignaturePort;
import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.shared.security.RsaKeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Infrastructure service responsible for canonical formatting and RSA signing of QR check-in tickets.
 */
@Service
@RequiredArgsConstructor
public class QrSignatureService implements QrSignaturePort {

    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper().registerModule(new JavaTimeModule());

    private final RsaKeyProperties rsaKeyProperties;

    /**
     * Generates a canonical UTF-8 JSON payload and signs it using SHA256withRSA.
     *
     * @param registrationId target registration ID
     * @param workshopId workshop ID
     * @param studentId student user ID
     * @param issuedAt exact timestamp of signing
     * @return cryptographically signed QrTicket value object
     */
    public QrTicket generateSignedTicket(
        UUID registrationId,
        UUID workshopId,
        UUID studentId,
        Instant issuedAt
    ) {
        try {
            Map<String, String> payloadMap = new LinkedHashMap<>();
            payloadMap.put("registrationId", registrationId.toString());
            payloadMap.put("workshopId", workshopId.toString());
            payloadMap.put("studentId", studentId.toString());
            payloadMap.put("issuedAt", issuedAt.toString());

            String canonicalJson = OBJECT_MAPPER.writeValueAsString(payloadMap);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(rsaKeyProperties.privateKey());
            signature.update(canonicalJson.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signature.sign();
            String base64Signature = Base64.getEncoder().encodeToString(
                signatureBytes
            );

            return new QrTicket(canonicalJson, base64Signature, issuedAt);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to generate RSA signed QR ticket: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Verifies a QR payload against its RSA-SHA256 signature using the public key.
     *
     * @param payload canonical UTF-8 JSON string
     * @param base64Signature Base64 encoded signature
     * @return true if valid signature, false otherwise
     */
    public boolean verifySignature(String payload, String base64Signature) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(rsaKeyProperties.publicKey());
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(
                Base64.getDecoder().decode(base64Signature)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
