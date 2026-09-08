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
    void extractRequiredSessionId_shouldReturnSessionId_whenSidPresent() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti", "STUDENT", SESSION_ID.toString(), Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");

        UUID result = SecurityContextUtils.extractRequiredSessionId(auth, "sid is required");
        assertThat(result).isEqualTo(SESSION_ID);
    }

    @Test
    void extractRequiredSessionId_shouldThrowBadRequest_whenSidMissing() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                USER_ID.toString(), null, "jti", "STUDENT", null, Instant.now(), Instant.now());
        TestingAuthenticationToken auth = new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");

        assertThatThrownBy(() -> SecurityContextUtils.extractRequiredSessionId(auth, "sid is required"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("sid is required");
    }
}
