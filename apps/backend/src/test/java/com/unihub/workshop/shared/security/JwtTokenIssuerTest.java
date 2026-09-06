package com.unihub.workshop.shared.security;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenIssuerTest {

    private static RSAPublicKey testPublicKey;
    private static RSAPrivateKey testPrivateKey;

    private JwtTokenIssuer jwtTokenIssuer;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        testPublicKey = (RSAPublicKey) keyPair.getPublic();
        testPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    @BeforeEach
    void setUp() {
        RsaKeyProperties rsaKeyProperties = mock(RsaKeyProperties.class);
        when(rsaKeyProperties.publicKey()).thenReturn(testPublicKey);
        when(rsaKeyProperties.privateKey()).thenReturn(testPrivateKey);

        SecurityProperties securityProperties = new SecurityProperties(List.of(), 15, 60, 7);
        jwtTokenIssuer = new JwtTokenIssuer(rsaKeyProperties, securityProperties);
    }

    @Test
    void shouldIncludeOrgClaimWhenOrganizationIdIsPresent() throws Exception {
        String userId = UUID.randomUUID().toString();
        String orgId = UUID.randomUUID().toString();

        String token = jwtTokenIssuer.issueAccessToken(userId, "ORGANIZER", orgId);

        SignedJWT signedJWT = SignedJWT.parse(token);
        assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo(userId);
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("role")).isEqualTo("ORGANIZER");
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("org")).isEqualTo(orgId);
        assertThat(signedJWT.getJWTClaimsSet().getJWTID()).isNotBlank();
        assertThat(signedJWT.getJWTClaimsSet().getIssuer()).isEqualTo("unihub");
    }

    @Test
    void shouldOmitOrgClaimWhenOrganizationIdIsNull() throws Exception {
        String userId = UUID.randomUUID().toString();

        String token = jwtTokenIssuer.issueAccessToken(userId, "STUDENT", null);

        SignedJWT signedJWT = SignedJWT.parse(token);
        assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo(userId);
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("role")).isEqualTo("STUDENT");
        assertThat(signedJWT.getJWTClaimsSet().getClaim("org")).isNull();
        assertThat(signedJWT.getJWTClaimsSet().getJWTID()).isNotBlank();
    }

    @Test
    void shouldOmitOrgClaimWhenOrganizationIdIsBlank() throws Exception {
        String userId = UUID.randomUUID().toString();

        String token = jwtTokenIssuer.issueAccessToken(userId, "STUDENT", "   ");

        SignedJWT signedJWT = SignedJWT.parse(token);
        assertThat(signedJWT.getJWTClaimsSet().getClaim("org")).isNull();
    }
}
