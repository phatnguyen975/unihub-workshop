package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${app.jwt.private-key-path:file:certs/private.pem}") String privateKeyPath,
            @Value("${app.jwt.public-key-path:file:certs/public.pem}") String publicKeyPath,
            @Value("${app.jwt.issuer:unihub}") String issuer) {
        this.issuer = issuer;
        RSAPrivateKey pKey = null;
        RSAPublicKey pubKey = null;
        try {
            pKey = loadPrivateKey(privateKeyPath);
            pubKey = loadPublicKey(publicKeyPath);
        } catch (Exception e) {
            try {
                var kpg = java.security.KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                var kp = kpg.generateKeyPair();
                pKey = (RSAPrivateKey) kp.getPrivate();
                pubKey = (RSAPublicKey) kp.getPublic();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to load RSA keys for JWT processing", e);
            }
        }
        this.privateKey = pKey;
        this.publicKey = pubKey;
    }

    public String generateAccessToken(User user) {
        return buildToken(user, ACCESS_TOKEN_TTL, "access");
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, REFRESH_TOKEN_TTL, "refresh");
    }

    public String generateRestrictedAccessToken(User user) {
        return buildToken(user, ACCESS_TOKEN_TTL, "restricted");
    }

    public TokenClaims parseToken(String token) throws JwtException {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        Instant issuedAt = claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
        Instant expiresAt = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
        String type = claims.get("type", String.class);
        String role = claims.get("role", String.class);
        return new TokenClaims(claims.getSubject(), claims.getId(), type, role, issuedAt, expiresAt);
    }

    private String buildToken(User user, Duration ttl, String tokenType) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim("role", user.getRole().name())
                .claim("type", tokenType)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private RSAPrivateKey loadPrivateKey(String path)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = readPem(path);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(spec);
    }

    private RSAPublicKey loadPublicKey(String path)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = readPem(path);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(spec);
    }

    private byte[] readPem(String path) throws IOException {
        Path p;
        if (path.startsWith("file:")) {
            p = Paths.get(URI.create(path));
        } else if (path.startsWith("classpath:")) {
            String resourcePath = path.substring("classpath:".length());
            try (var is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null)
                    throw new IOException("Resource not found: " + resourcePath);
                return is.readAllBytes();
            }
        } else {
            p = Paths.get(path);
        }
        String pem = Files.readString(p);
        pem = pem.replaceAll("-----BEGIN [A-Z ]+-----", "");
        pem = pem.replaceAll("-----END [A-Z ]+-----", "");
        pem = pem.replaceAll("\\s", "");
        return Base64.getDecoder().decode(pem);
    }

    public record TokenClaims(
            String subject,
            String jti,
            String type,
            String role,
            Instant issuedAt,
            Instant expiresAt) {
    }
}
