package com.unihub.workshop.shared.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private JwtClaimsExtractor claimsExtractor;

    @Mock
    private RedisJtiValidator jtiValidator;

    @Mock
    private HandlerExceptionResolver exceptionResolver;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldRejectBlacklistedJti() throws Exception {
        // Arrange
        request.setRequestURI(
            "/api/v1/workshops/00000000-0000-4000-8000-000000000001/attendances/summary"
        );
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        Jwt jwt = mock(Jwt.class);
        when(jwtDecoder.decode(token)).thenReturn(jwt);

        String jti = UUID.randomUUID().toString();
        String sid = UUID.randomUUID().toString();
        Instant now = Instant.now();
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            jti,
            "STUDENT",
            sid,
            now,
            now.plusSeconds(3600)
        );

        when(claimsExtractor.extract(jwt)).thenReturn(principal);
        when(jtiValidator.isTokenValid(jti)).thenReturn(false); // Blacklisted!

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(exceptionResolver).resolveException(
            eq(request),
            eq(response),
            eq(null),
            any(AuthenticationCredentialsNotFoundException.class)
        );
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void summaryRejectsInvalidJwtBeforeChain() throws Exception {
        request.setRequestURI(
            "/api/v1/workshops/00000000-0000-4000-8000-000000000001/attendances/summary"
        );
        request.addHeader("Authorization", "Bearer invalid");
        when(jwtDecoder.decode("invalid")).thenThrow(
            new JwtException("invalid token")
        );
        filter.doFilterInternal(request, response, filterChain);
        verify(exceptionResolver).resolveException(
            eq(request),
            eq(response),
            eq(null),
            any(AuthenticationCredentialsNotFoundException.class)
        );
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void summaryRevocationOutageFailsClosedBeforeChain() throws Exception {
        request.setRequestURI(
            "/api/v1/workshops/00000000-0000-4000-8000-000000000001/attendances/summary"
        );
        request.addHeader("Authorization", "Bearer token");
        Jwt jwt = mock(Jwt.class);
        Instant now = Instant.now();
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "jti",
            "STAFF",
            UUID.randomUUID().toString(),
            now,
            now.plusSeconds(60)
        );
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(claimsExtractor.extract(jwt)).thenReturn(principal);
        when(jtiValidator.isTokenValid("jti")).thenThrow(
            new ServiceUnavailableException("unavailable")
        );
        filter.doFilterInternal(request, response, filterChain);
        verify(exceptionResolver).resolveException(
            eq(request),
            eq(response),
            eq(null),
            any(ServiceUnavailableException.class)
        );
        verify(filterChain, never()).doFilter(request, response);
    }
}
