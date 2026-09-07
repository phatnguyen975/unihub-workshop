package com.unihub.workshop.shared.security;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class JwtTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public JwtTokenIssuer(RsaKeyProperties rsaKeyProperties, SecurityProperties properties) {
        this.properties = properties;
        RSAKey rsaKey = new RSAKey.Builder(rsaKeyProperties.publicKey())
                .privateKey(rsaKeyProperties.privateKey())
                .build();
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    public IssuedToken issueToken(String subject, String role, String organizationId, String sessionId) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        long ttlMinutes = properties.accessTokenTtlMinutes();
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = now.plusSeconds(ttlMinutes * 60);

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("unihub")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(subject)
                .claim("role", role)
                .id(jti);

        if (organizationId != null && !organizationId.isBlank()) {
            claimsBuilder.claim("org", organizationId);
        }

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID (sid) is required to issue access token");
        }
        claimsBuilder.claim("sid", sessionId);

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claimsBuilder.build())).getTokenValue();
        return new IssuedToken(token, jti, expiresAt);
    }

    public String issueAccessToken(String subject, String role, String organizationId, String sessionId) {
        return issueToken(subject, role, organizationId, sessionId).token();
    }
}
