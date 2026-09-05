package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.security.PublicKeyService;
import com.unihub.workshop.shared.security.RedisJtiValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.unihub.workshop.WorkshopApplication.class, properties = {
        "spring.flyway.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "datasource.primary.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.replica.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.primary.driver-class-name=org.h2.Driver",
        "datasource.replica.driver-class-name=org.h2.Driver"
})
@ActiveProfiles("test")
class AuthControllerPublicKeyTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private PublicKeyService publicKeyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RedisJtiValidator redisJtiValidator;

    @BeforeEach
    void setUp() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        when(redisJtiValidator.isTokenValid(anyString())).thenReturn(true);
    }

    private Jwt createMockJwt(String role) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", UUID.randomUUID().toString())
                .claim("org", UUID.randomUUID().toString())
                .claim("jti", "jti-123")
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void getPublicKey_withStaff_returns200() throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("STAFF"));
        when(publicKeyService.getKeyId()).thenReturn("mock-key-id");
        when(publicKeyService.getPublicKeyPem())
                .thenReturn("-----BEGIN PUBLIC KEY-----\nMOCK\n-----END PUBLIC KEY-----");
        when(publicKeyService.getIssuedAt()).thenReturn(Instant.parse("2026-09-03T05:42:00Z"));

        mockMvc.perform(get("/api/v1/auth/public-key")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keyId").value("mock-key-id"))
                .andExpect(jsonPath("$.data.publicKeyPem")
                        .value("-----BEGIN PUBLIC KEY-----\nMOCK\n-----END PUBLIC KEY-----"))
                .andExpect(jsonPath("$.data.issuedAt").value("2026-09-03T05:42:00Z"));
    }

    @Test
    void getPublicKey_withOrganizer_returns403() throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("ORGANIZER"));

        mockMvc.perform(get("/api/v1/auth/public-key")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:forbidden"));
    }

    @Test
    void getPublicKey_withStudent_returns403() throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("STUDENT"));

        mockMvc.perform(get("/api/v1/auth/public-key")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:forbidden"));
    }

    @Test
    void getPublicKey_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/public-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:unauthorized"));
    }

    @Test
    void getPublicKey_withExpiredJwt_returns401() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Expired"));

        mockMvc.perform(get("/api/v1/auth/public-key")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:unauthorized"));
    }

    @Test
    void getPublicKey_whenRedisIsDown_returns503WithRetryAfter() throws Exception {
        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("STAFF"));
        when(redisJtiValidator.isTokenValid(anyString()))
                .thenThrow(new ServiceUnavailableException("Failed to check token revocation status. Failing closed."));

        mockMvc.perform(get("/api/v1/auth/public-key")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:service-unavailable"));
    }
}
