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

    public String issueAccessToken(String subject, String role, String organizationId) {
        Instant now = Instant.now();
        long ttlMinutes = properties.accessTokenTtlMinutes();

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("unihub")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlMinutes * 60))
                .subject(subject)
                .claim("role", role)
                .id(UUID.randomUUID().toString()); // jti

        if (organizationId != null && !organizationId.isBlank()) {
            claimsBuilder.claim("org", organizationId);
        }

        return jwtEncoder.encode(JwtEncoderParameters.from(claimsBuilder.build())).getTokenValue();
    }
}
