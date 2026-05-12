package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final SecretKey secretKey;
    private final String issuer;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:dev-secret-please-change-dev-secret-please-change}") String secret,
            @Value("${app.jwt.issuer:unihub}") String issuer) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
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
                .verifyWith(secretKey)
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
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
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
