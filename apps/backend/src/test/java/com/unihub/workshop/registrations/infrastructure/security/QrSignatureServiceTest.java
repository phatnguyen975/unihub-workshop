package com.unihub.workshop.registrations.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.shared.security.RsaKeyProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrSignatureServiceTest {

    private QrSignatureService qrSignatureService;
    private RsaKeyProperties rsaKeyProperties;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        rsaKeyProperties = new RsaKeyProperties(
            (RSAPrivateKey) keyPair.getPrivate(),
            (RSAPublicKey) keyPair.getPublic()
        );

        qrSignatureService = new QrSignatureService(rsaKeyProperties);
    }

    @Test
    @DisplayName(
        "generateSignedTicket: creates valid signed ticket verifiable with public key"
    )
    void generateSignedTicket_Valid_CreatesVerifiableTicket() {
        UUID registrationId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant issuedAt = Instant.now();

        QrTicket ticket = qrSignatureService.generateSignedTicket(
            registrationId,
            workshopId,
            studentId,
            issuedAt
        );

        assertThat(ticket).isNotNull();
        assertThat(ticket.qrPayload()).contains(registrationId.toString());
        assertThat(ticket.qrPayload()).contains(workshopId.toString());
        assertThat(ticket.qrPayload()).contains(studentId.toString());
        assertThat(ticket.qrSignature()).isNotBlank();
        assertThat(ticket.qrIssuedAt()).isEqualTo(issuedAt);

        boolean isValid = qrSignatureService.verifySignature(
            ticket.qrPayload(),
            ticket.qrSignature()
        );
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName(
        "verifySignature: returns false when payload has been tampered with"
    )
    void verifySignature_TamperedPayload_ReturnsFalse() {
        UUID registrationId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant issuedAt = Instant.now();

        QrTicket ticket = qrSignatureService.generateSignedTicket(
            registrationId,
            workshopId,
            studentId,
            issuedAt
        );

        String tamperedPayload = ticket
            .qrPayload()
            .replace(studentId.toString(), UUID.randomUUID().toString());

        boolean isValid = qrSignatureService.verifySignature(
            tamperedPayload,
            ticket.qrSignature()
        );
        assertThat(isValid).isFalse();
    }
}
