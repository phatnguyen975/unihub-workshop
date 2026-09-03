package com.unihub.workshop.shared.security;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PublicKeyService {

    private final String publicKeyPem;
    private final String keyId;

    public PublicKeyService(RsaKeyProperties rsaKeyProperties) {
        RSAPublicKey publicKey = rsaKeyProperties.publicKey();
        byte[] encoded = publicKey.getEncoded();

        // Precompute PEM
        String base64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(encoded);
        this.publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";

        // Precompute Key ID
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(encoded);
            this.keyId = HexFormat.of().withLowerCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public String getKeyId() {
        return keyId;
    }

    public Instant getIssuedAt() {
        return Instant.now();
    }
}
