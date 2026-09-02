package com.unihub.workshop.shared.security;

import jakarta.servlet.FilterChain;
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
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        String token = "some.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        Jwt jwt = mock(Jwt.class);
        when(jwtDecoder.decode(token)).thenReturn(jwt);

        String jti = UUID.randomUUID().toString();
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), jti, "STUDENT", null, null);

        when(claimsExtractor.extract(jwt)).thenReturn(principal);
        when(jtiValidator.isTokenValid(jti)).thenReturn(false); // Blacklisted!

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(exceptionResolver).resolveException(eq(request), eq(response), eq(null),
                any(AuthenticationCredentialsNotFoundException.class));
        verify(filterChain, never()).doFilter(request, response);
    }
}
