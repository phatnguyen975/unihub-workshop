package com.unihub.workshop.shared.security;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

public final class SecurityContextUtils {

    private SecurityContextUtils() {
    }

    public static UUID extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        if (authentication.getPrincipal() instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            return UUID.fromString(jwtPrincipal.sub());
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        } else {
            throw new UnauthorizedException("Unauthorized: invalid token principal");
        }
    }

    public static Optional<UUID> extractOptionalSessionId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        String sid = null;
        if (authentication.getPrincipal() instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            sid = jwtPrincipal.sid();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            sid = jwt.getClaimAsString("sid");
        }

        if (sid == null || sid.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(sid));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid session ID in token");
        }
    }

    public static UUID extractRequiredSessionId(Authentication authentication, String missingMessage) {
        return extractOptionalSessionId(authentication)
                .orElseThrow(() -> new BadRequestException(missingMessage));
    }
}
