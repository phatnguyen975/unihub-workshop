package com.unihub.workshop.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class JwtClaimsExtractorTest {

    private final Instant now = Instant.now();
    private final Instant later = now.plus(1, ChronoUnit.HOURS);

    @Test
    void shouldExtractPrincipal_whenJwtHasAllRequiredClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .claim("sid", "session-999")
                .issuedAt(now)
                .expiresAt(later)
                .build();

        JwtClaimsExtractor extractor = new JwtClaimsExtractor();
        JwtAuthenticatedPrincipal principal = extractor.extract(jwt);

        assertNotNull(principal);
        assertEquals("user-123", principal.sub());
        assertEquals("org-456", principal.org());
        assertEquals("jti-789", principal.jti());
        assertEquals("ORGANIZER", principal.role());
        assertEquals("session-999", principal.sid());
        assertEquals(now, principal.iat());
        assertEquals(later, principal.exp());
    }

    @Test
    void shouldExtractPrincipal_withSidClaimWhenPresent() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .claim("sid", "session-999")
                .issuedAt(now)
                .expiresAt(later)
                .build();

        JwtClaimsExtractor extractor = new JwtClaimsExtractor();
        JwtAuthenticatedPrincipal principal = extractor.extract(jwt);

        assertNotNull(principal);
        assertEquals("user-123", principal.sub());
        assertEquals("session-999", principal.sid());
    }

    @Test
    void shouldThrowException_whenSubIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .claim("sid", "session-999")
                .issuedAt(now)
                .expiresAt(later)
                .build();

        JwtClaimsExtractor extractor = new JwtClaimsExtractor();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> extractor.extract(jwt));
        assertEquals("JWT is missing required claim: sub", ex.getMessage());
    }

    @Test
    void shouldThrowException_whenOrgIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .claim("sid", "session-999")
                .issuedAt(now)
                .expiresAt(later)
                .build();

        JwtClaimsExtractor extractor = new JwtClaimsExtractor();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> extractor.extract(jwt));
        assertEquals("JWT is missing required claim: org", ex.getMessage());
    }

    @Test
    void shouldThrowException_whenJtiIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("role", "ORGANIZER")
                .claim("sid", "session-999")
                .issuedAt(now)
                .expiresAt(later)
                .build();

        JwtClaimsExtractor extractor = new JwtClaimsExtractor();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> extractor.extract(jwt));
        assertEquals("JWT is missing required claim: jti", ex.getMessage());
    }

    @Test
    void shouldThrowException_whenRoleIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("sid", "session-999")
                .issuedAt(now)
                .expiresAt(later)
                .build();

        JwtClaimsExtractor extractor = new JwtClaimsExtractor();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> extractor.extract(jwt));
        assertEquals("JWT is missing required claim: role", ex.getMessage());
    }

    @Test
    void shouldThrowException_whenSidIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .issuedAt(now)
                .expiresAt(later)
                .build();

        JwtClaimsExtractor extractor = new JwtClaimsExtractor();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> extractor.extract(jwt));
        assertEquals("JWT is missing required claim: sid", ex.getMessage());
    }

    @Test
    void shouldThrowException_whenIatIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .claim("sid", "session-999")
                .expiresAt(later)
                .build();

        JwtClaimsExtractor extractor = new JwtClaimsExtractor();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> extractor.extract(jwt));
        assertEquals("JWT is missing required claim: iat", ex.getMessage());
    }

    @Test
    void shouldThrowException_whenExpIsMissing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .claim("sid", "session-999")
                .issuedAt(now)
                .build();

        JwtClaimsExtractor extractor = new JwtClaimsExtractor();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> extractor.extract(jwt));
        assertEquals("JWT is missing required claim: exp", ex.getMessage());
    }
}
