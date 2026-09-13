package com.unihub.workshop.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.shared.exception.InternalServerException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicKeyServiceTest {

    private PublicKeyService publicKeyService;
    private RSAPublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RsaKeyProperties rsaKeyProperties = new RsaKeyProperties(
            privateKey,
            publicKey
        );
        publicKeyService = new PublicKeyService(rsaKeyProperties);
    }

    @Test
    void getPublicKeyPem_returnsCorrectFormat() {
        String pem = publicKeyService.getPublicKeyPem();

        assertThat(pem).startsWith("-----BEGIN PUBLIC KEY-----\n");
        assertThat(pem).endsWith("\n-----END PUBLIC KEY-----");
        assertThat(pem).contains("\n"); // check for line breaks

        String base64Body = pem
            .replace("-----BEGIN PUBLIC KEY-----\n", "")
            .replace("\n-----END PUBLIC KEY-----", "")
            .replaceAll("\n", "");

        byte[] decoded = Base64.getDecoder().decode(base64Body);
        assertThat(decoded).isEqualTo(publicKey.getEncoded());
    }

    @Test
    void getKeyId_isDeterministic() {
        String keyId1 = publicKeyService.getKeyId();
        String keyId2 = publicKeyService.getKeyId();

        assertThat(keyId1).isNotBlank();
        assertThat(keyId1).isEqualTo(keyId2);
        assertThat(keyId1).isLowerCase();
        assertThat(keyId1).matches("^[a-f0-9]{64}$"); // SHA-256 hex is 64 chars
    }

    @Test
    void getKeyId_changesWithDifferentKey() throws Exception {
        String keyId1 = publicKeyService.getKeyId();

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        RsaKeyProperties newProperties = new RsaKeyProperties(
            (RSAPrivateKey) keyPair.getPrivate(),
            (RSAPublicKey) keyPair.getPublic()
        );
        PublicKeyService newService = new PublicKeyService(newProperties);
        String keyId2 = newService.getKeyId();

        assertThat(keyId1).isNotEqualTo(keyId2);
    }

    @Test
    void getIssuedAt_isPastOrPresent() {
        Instant issuedAt = publicKeyService.getIssuedAt();

        assertThat(issuedAt).isNotNull();
        assertThat(issuedAt).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldThrowInternalServerExceptionWhenRsaKeyPropertiesIsNull() {
        assertThatThrownBy(() -> new PublicKeyService(null))
            .isInstanceOf(InternalServerException.class)
            .hasMessage("RSA public key is not loaded.");
    }

    @Test
    void shouldThrowInternalServerExceptionWhenPublicKeyIsNull() {
        RsaKeyProperties invalidProperties = new RsaKeyProperties(null, null);
        assertThatThrownBy(() -> new PublicKeyService(invalidProperties))
            .isInstanceOf(InternalServerException.class)
            .hasMessage("RSA public key is not loaded.");
    }
}
