package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer;
    private final ResourceLoader resourceLoader;

    public JwtTokenProvider(
            @Value("${app.jwt.private-key-path:file:../certs/private.pem}") String privateKeyPath,
            @Value("${app.jwt.public-key-path:file:../certs/public.pem}") String publicKeyPath,
            @Value("${app.jwt.issuer:unihub}") String issuer,
            ResourceLoader resourceLoader) {
        this.issuer = issuer;
        this.resourceLoader = resourceLoader;
        try {
            this.privateKey = loadPrivateKey(privateKeyPath);
            this.publicKey = loadPublicKey(publicKeyPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load static RSA keys for JWT processing", e);
        }
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

    public String generateTicketToken(UUID registrationId, UUID workshopId, UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .claim("type", "ticket")
                .claim("registration_id", registrationId.toString())
                .claim("workshop_id", workshopId.toString())
                .claim("user_id", userId.toString())
                .claim("issued_at", now.toString())
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
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

    private byte[] readPem(String location) throws IOException {
        if (location == null || !location.startsWith("file:")) {
            throw new IOException("JWT key location must use file: prefix. Provided: " + location);
        }

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException("JWT key file not found at location: " + location);
        }

        String pem;
        try (var inputStream = resource.getInputStream()) {
            pem = new String(inputStream.readAllBytes());
        }

        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        return Base64.getDecoder().decode(normalized);
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
