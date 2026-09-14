package com.unihub.workshop.shared.security;

import java.time.Instant;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtClaimsExtractor {

    public JwtAuthenticatedPrincipal extract(Jwt jwt) {
        String sub = extractOrThrow(jwt, "sub");
        String orgClaim = jwt.getClaimAsString("org");
        String org =
            orgClaim != null && !orgClaim.isBlank() ? orgClaim.trim() : null;
        String jti = extractOrThrow(jwt, "jti");
        String role = extractOrThrow(jwt, "role");
        String sid = extractOrThrow(jwt, "sid");

        Instant iat = jwt.getIssuedAt();
        if (iat == null) {
            throw new IllegalArgumentException(
                "JWT is missing required claim: iat"
            );
        }

        Instant exp = jwt.getExpiresAt();
        if (exp == null) {
            throw new IllegalArgumentException(
                "JWT is missing required claim: exp"
            );
        }

        return new JwtAuthenticatedPrincipal(
            sub,
            org,
            jti,
            role,
            sid,
            iat,
            exp
        );
    }

    private String extractOrThrow(Jwt jwt, String claimName) {
        String claim = jwt.getClaimAsString(claimName);
        if (claim == null || claim.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "JWT is missing required claim: " + claimName
            );
        }
        return claim;
    }
}
