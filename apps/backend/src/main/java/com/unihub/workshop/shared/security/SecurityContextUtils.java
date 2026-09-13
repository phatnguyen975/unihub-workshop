package com.unihub.workshop.shared.security;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SecurityContextUtils {

    private SecurityContextUtils() {}

    public static UUID extractUserId(Authentication authentication) {
        validateAuthentication(authentication);

        if (
            authentication.getPrincipal() instanceof
                JwtAuthenticatedPrincipal jwtPrincipal
        ) {
            return UUID.fromString(jwtPrincipal.sub());
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        } else {
            throw new UnauthorizedException(
                "Unauthorized: invalid token principal"
            );
        }
    }

    public static UUID extractSessionId(Authentication authentication) {
        validateAuthentication(authentication);

        String sid = null;
        if (
            authentication.getPrincipal() instanceof
                JwtAuthenticatedPrincipal jwtPrincipal
        ) {
            sid = jwtPrincipal.sid();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            sid = jwt.getClaimAsString("sid");
        }

        if (sid == null || sid.isBlank()) {
            throw new BadRequestException(
                "Current session ID (sid) is required in token"
            );
        }

        try {
            return UUID.fromString(sid);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid session ID in token");
        }
    }

    public static UUID extractOrganizationId(Authentication authentication) {
        return extractOptionalOrganizationId(authentication).orElseThrow(() ->
            new ForbiddenException("Missing organization context")
        );
    }

    public static Optional<UUID> extractOptionalOrganizationId(
        Authentication authentication
    ) {
        validateAuthentication(authentication);

        String org = null;
        if (
            authentication.getPrincipal() instanceof
                JwtAuthenticatedPrincipal jwtPrincipal
        ) {
            org = jwtPrincipal.org();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            org = jwt.getClaimAsString("org");
        }

        if (org == null || org.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(org));
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Invalid organization context");
        }
    }

    public static String extractRole(Authentication authentication) {
        validateAuthentication(authentication);

        if (
            authentication.getPrincipal() instanceof
                JwtAuthenticatedPrincipal jwtPrincipal
        ) {
            if (jwtPrincipal.role() != null && !jwtPrincipal.role().isBlank()) {
                return jwtPrincipal.role();
            }
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            String role = jwt.getClaimAsString("role");
            if (role != null && !role.isBlank()) {
                return role;
            }
        }

        if (authentication.getAuthorities() != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String auth = authority.getAuthority();
                if (auth != null && auth.startsWith("ROLE_")) {
                    return auth.substring(5);
                }
            }
        }

        throw new UnauthorizedException("Unauthorized: missing role in token");
    }

    public static String extractJti(Authentication authentication) {
        validateAuthentication(authentication);

        if (
            authentication.getPrincipal() instanceof
                JwtAuthenticatedPrincipal jwtPrincipal
        ) {
            if (jwtPrincipal.jti() != null && !jwtPrincipal.jti().isBlank()) {
                return jwtPrincipal.jti();
            }
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            if (jwt.getId() != null && !jwt.getId().isBlank()) {
                return jwt.getId();
            }
        }

        throw new UnauthorizedException(
            "Unauthorized: missing token identifier (jti)"
        );
    }

    public static Instant extractExpiration(Authentication authentication) {
        validateAuthentication(authentication);

        if (
            authentication.getPrincipal() instanceof
                JwtAuthenticatedPrincipal jwtPrincipal
        ) {
            if (jwtPrincipal.exp() != null) {
                return jwtPrincipal.exp();
            }
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            if (jwt.getExpiresAt() != null) {
                return jwt.getExpiresAt();
            }
        }

        throw new UnauthorizedException(
            "Unauthorized: missing token expiration (exp)"
        );
    }

    public static void verifyRole(
        Authentication authentication,
        String expectedRole
    ) {
        String actualRole = extractRole(authentication);
        if (!expectedRole.equalsIgnoreCase(actualRole)) {
            throw new ForbiddenException("Access denied");
        }
    }

    public static String extractToken(Authentication authentication) {
        validateAuthentication(authentication);

        if (
            authentication instanceof
                org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuth
        ) {
            return jwtAuth.getToken().getTokenValue();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getTokenValue();
        }
        if (authentication.getCredentials() instanceof Jwt jwt) {
            return jwt.getTokenValue();
        }
        if (
            authentication.getCredentials() instanceof String credStr &&
            !credStr.isBlank()
        ) {
            return credStr;
        }
        if (
            authentication.getCredentials() != null &&
            !(authentication.getCredentials() instanceof Jwt) &&
            !authentication.getCredentials().toString().isBlank()
        ) {
            return authentication.getCredentials().toString();
        }

        return "";
    }

    private static void validateAuthentication(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthorizedException(
                "Full authentication is required to access this resource"
            );
        }
        if (
            !authentication.isAuthenticated() &&
            !(authentication instanceof
                org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken)
        ) {
            throw new UnauthorizedException(
                "Full authentication is required to access this resource"
            );
        }
    }
}
