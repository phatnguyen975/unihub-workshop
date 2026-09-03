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

    private final RsaKeyProperties rsaKeyProperties;

    public PublicKeyService(RsaKeyProperties rsaKeyProperties) {
        this.rsaKeyProperties = rsaKeyProperties;
    }

    public String getPublicKeyPem() {
        RSAPublicKey publicKey = rsaKeyProperties.publicKey();
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    public String getKeyId() {
        try {
            RSAPublicKey publicKey = rsaKeyProperties.publicKey();
            byte[] encoded = publicKey.getEncoded();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(encoded);
            return HexFormat.of().withLowerCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public Instant getIssuedAt() {
        return Instant.now();
    }
}
