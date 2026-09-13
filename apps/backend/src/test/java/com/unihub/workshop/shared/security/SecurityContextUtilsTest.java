package com.unihub.workshop.shared.security;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextUtilsTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Test
    void extractUserId_shouldReturnUserId_whenJwtAuthenticatedPrincipal() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti", "STUDENT", SESSION_ID.toString(), Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");

        UUID result = SecurityContextUtils.extractUserId(auth);
        assertThat(result).isEqualTo(USER_ID);
    }

    @Test
    void extractUserId_shouldReturnUserId_whenJwt() {
        Jwt jwt = Jwt.withTokenValue("mock")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .build();
        TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null, "ROLE_STUDENT");

        UUID result = SecurityContextUtils.extractUserId(auth);
        assertThat(result).isEqualTo(USER_ID);
    }

    @Test
    void extractUserId_shouldThrowUnauthorized_whenNullAuthOrUnauthenticated() {
        assertThatThrownBy(() -> SecurityContextUtils.extractUserId(null))
                .isInstanceOf(UnauthorizedException.class);

        TestingAuthenticationToken unauth = new TestingAuthenticationToken("user", "pass");
        unauth.setAuthenticated(false);
        assertThatThrownBy(() -> SecurityContextUtils.extractUserId(unauth))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void extractSessionId_shouldReturnSessionId_whenSidPresent() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti", "STUDENT", SESSION_ID.toString(), Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");

        UUID result = SecurityContextUtils.extractSessionId(auth);
        assertThat(result).isEqualTo(SESSION_ID);
    }

    @Test
    void extractSessionId_shouldThrowBadRequest_whenSidMissing() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti", "STUDENT", null, Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");

        assertThatThrownBy(() -> SecurityContextUtils.extractSessionId(auth))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Current session ID (sid) is required in token");
    }

    @Test
    void extractOrganizationId_shouldReturnOrgId_whenValidInPrincipal() {
        UUID orgId = UUID.randomUUID();
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), orgId.toString(), "jti", "ORGANIZER", SESSION_ID.toString(), Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_ORGANIZER");

        UUID result = SecurityContextUtils.extractOrganizationId(auth);
        assertThat(result).isEqualTo(orgId);
    }

    @Test
    void extractOrganizationId_shouldReturnOrgId_whenValidInJwt() {
        UUID orgId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("mock")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .claim("org", orgId.toString())
                .build();
        TestingAuthenticationToken auth = new TestingAuthenticationToken(jwt, null, "ROLE_ORGANIZER");

        UUID result = SecurityContextUtils.extractOrganizationId(auth);
        assertThat(result).isEqualTo(orgId);
    }

    @Test
    void extractOrganizationId_shouldThrowForbidden_whenOrgMissing() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti", "STUDENT", SESSION_ID.toString(), Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");

        assertThatThrownBy(() -> SecurityContextUtils.extractOrganizationId(auth))
                .isInstanceOf(com.unihub.workshop.shared.exception.ForbiddenException.class)
                .hasMessage("Missing organization context");
    }

    @Test
    void extractOrganizationId_shouldThrowForbidden_whenOrgInvalidUuid() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), "not-a-uuid", "jti", "ORGANIZER", SESSION_ID.toString(), Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_ORGANIZER");

        assertThatThrownBy(() -> SecurityContextUtils.extractOrganizationId(auth))
                .isInstanceOf(com.unihub.workshop.shared.exception.ForbiddenException.class)
                .hasMessage("Invalid organization context");
    }

    @Test
    void extractRole_shouldReturnRole_whenInPrincipalOrJwtOrAuthority() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti", "ORGANIZER", null, Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_ORGANIZER");
        assertThat(SecurityContextUtils.extractRole(auth)).isEqualTo("ORGANIZER");

        Jwt jwt = Jwt.withTokenValue("mock").header("alg", "none").subject(USER_ID.toString()).claim("role", "STAFF").build();
        TestingAuthenticationToken jwtAuth = new TestingAuthenticationToken(jwt, null, "ROLE_STAFF");
        assertThat(SecurityContextUtils.extractRole(jwtAuth)).isEqualTo("STAFF");
    }

    @Test
    void extractJtiAndExpirationAndToken_shouldExtractSuccessfully() {
        Instant exp = Instant.now().plusSeconds(3600);
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti-test", "STUDENT", null, Instant.now(), exp);
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, "raw-token", "ROLE_STUDENT");

        assertThat(SecurityContextUtils.extractJti(auth)).isEqualTo("jti-test");
        assertThat(SecurityContextUtils.extractExpiration(auth)).isEqualTo(exp);
        assertThat(SecurityContextUtils.extractToken(auth)).isEqualTo("raw-token");
    }

    @Test
    void extractToken_shouldExtractFromJwtAuthenticationToken() {
        Jwt jwt = Jwt.withTokenValue("jwt-token-value")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .claim("jti", "jti-val")
                .build();
        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuth =
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);

        assertThat(SecurityContextUtils.extractToken(jwtAuth)).isEqualTo("jwt-token-value");
    }

    @Test
    void verifyRole_shouldSucceedWhenRoleMatches() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti", "ORGANIZER", null, Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_ORGANIZER");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                SecurityContextUtils.verifyRole(auth, "ORGANIZER"));
    }

    @Test
    void verifyRole_shouldThrowForbiddenWhenRoleMismatches() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti", "STAFF", null, Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_STAFF");

        assertThatThrownBy(() -> SecurityContextUtils.verifyRole(auth, "ORGANIZER"))
                .isInstanceOf(com.unihub.workshop.shared.exception.ForbiddenException.class)
                .hasMessage("Access denied");
    }
}
