package com.unihub.workshop.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        com.unihub.workshop.WorkshopApplication.class, // the main application class
        SecurityConfigTest.DummyController.class
}, properties = {
        "spring.flyway.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "datasource.primary.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.replica.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.primary.driver-class-name=org.h2.Driver",
        "datasource.replica.driver-class-name=org.h2.Driver"
})
class SecurityConfigTest {

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RedisJtiValidator redisJtiValidator;

    @RestController
    static class DummyController {
        @GetMapping("/api/v1/admin/ingestion-jobs")
        public String secureEndpoint() {
            return "ok";
        }

        @GetMapping("/api/v1/payments/webhook")
        public String publicEndpoint() {
            return "ok";
        }
    }

    @Test
    void shouldReturn401_whenNoTokenProvided() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion-jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:unihub:error:unauthorized"));
    }

    @Test
    void shouldReturn200_whenValidTokenProvided() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS))
                .build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(redisJtiValidator.isTokenValid("jti-789")).thenReturn(true);

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn503_whenRedisIsUnavailable() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS))
                .build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(redisJtiValidator.isTokenValid("jti-789"))
                .thenThrow(new JtiValidationException("Redis down", null));

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:unihub:error:service-unavailable"));
    }

    @Test
    void shouldReturn401_whenTokenIsRevoked() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "ORGANIZER")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS))
                .build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(redisJtiValidator.isTokenValid("jti-789")).thenReturn(false);

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:unihub:error:unauthorized"));
    }

    @Test
    void shouldReturn403_whenRoleIsStudent() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "org-456")
                .claim("jti", "jti-789")
                .claim("role", "STUDENT")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS))
                .build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(redisJtiValidator.isTokenValid("jti-789")).thenReturn(true);

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:unihub:error:forbidden"));
    }

    @Test
    void shouldReturn200_forPublicEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/payments/webhook"))
                .andExpect(status().isOk());
    }
}
