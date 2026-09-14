package com.unihub.workshop.shared.security;

import java.time.Instant;

public record JwtAuthenticatedPrincipal(
    String sub,
    String org,
    String jti,
    String role,
    String sid,
    Instant iat,
    Instant exp
) {}
