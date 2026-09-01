package com.unihub.workshop.shared.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

public class JwtClaimsExtractor {

    public JwtAuthenticatedPrincipal extract(Jwt jwt) {
        String sub = extractOrThrow(jwt, "sub");
        String org = extractOrThrow(jwt, "org");
        String jti = extractOrThrow(jwt, "jti");
        String role = extractOrThrow(jwt, "role");

        Instant iat = jwt.getIssuedAt();
        if (iat == null) {
            throw new IllegalArgumentException("JWT is missing required claim: iat");
        }

        Instant exp = jwt.getExpiresAt();
        if (exp == null) {
            throw new IllegalArgumentException("JWT is missing required claim: exp");
        }

        return new JwtAuthenticatedPrincipal(sub, org, jti, role, iat, exp);
    }

    private String extractOrThrow(Jwt jwt, String claimName) {
        String claim = jwt.getClaimAsString(claimName);
        if (claim == null || claim.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT is missing required claim: " + claimName);
        }
        return claim;
    }
}
