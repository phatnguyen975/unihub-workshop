package com.unihub.workshop.shared.security;

import java.time.Instant;

public record IssuedToken(String token, String jti, Instant expiresAt) {}
