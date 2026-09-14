package com.unihub.workshop.shared.security;

import com.unihub.workshop.shared.exception.AppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final JwtClaimsExtractor claimsExtractor;
    private final RedisJtiValidator jtiValidator;
    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationFilter(
        JwtDecoder jwtDecoder,
        JwtClaimsExtractor claimsExtractor,
        RedisJtiValidator jtiValidator,
        HandlerExceptionResolver exceptionResolver
    ) {
        this.jwtDecoder = jwtDecoder;
        this.claimsExtractor = claimsExtractor;
        this.jtiValidator = jtiValidator;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                Jwt jwt = jwtDecoder.decode(token);

                JwtAuthenticatedPrincipal principal = claimsExtractor.extract(
                    jwt
                );

                if (!jtiValidator.isTokenValid(principal.jti())) {
                    throw new AuthenticationCredentialsNotFoundException(
                        "Token has been revoked"
                    );
                }

                List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + principal.role())
                );
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        principal,
                        token,
                        authorities
                    );
                SecurityContextHolder.getContext().setAuthentication(
                    authentication
                );
            }
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            exceptionResolver.resolveException(
                request,
                response,
                null,
                new AuthenticationCredentialsNotFoundException(
                    ex.getMessage(),
                    ex
                )
            );
        } catch (AuthenticationException | AppException ex) {
            exceptionResolver.resolveException(request, response, null, ex);
        }
    }
}
