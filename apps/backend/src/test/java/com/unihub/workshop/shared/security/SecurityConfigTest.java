package com.unihub.workshop.shared.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.AppException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = {
        com.unihub.workshop.WorkshopApplication.class,
        SecurityConfigTest.DummyController.class,
    },
    properties = {
        "spring.flyway.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "datasource.primary.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.replica.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.primary.driver-class-name=org.h2.Driver",
        "datasource.replica.driver-class-name=org.h2.Driver",
    }
)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class SecurityConfigTest {

    private final WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        when(
            redisJtiValidator.isTokenValid(ArgumentMatchers.anyString())
        ).thenReturn(true);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RedisJtiValidator redisJtiValidator;

    @RestController
    static class DummyController {

        @GetMapping("/api/v1/admin/dummy")
        public String adminEndpoint() {
            return "ok";
        }

        @GetMapping("/api/v1/workshops")
        public String authEndpoint() {
            return "ok";
        }

        @GetMapping("/api/v1/payments/webhook")
        public String publicEndpoint() {
            return "ok";
        }
    }

    @Test
    void shouldReturn401_whenNoTokenProvided() throws Exception {
        mockMvc
            .perform(get("/api/v1/workshops"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn200_whenValidTokenProvided() throws Exception {
        Jwt validJwt = Jwt.withTokenValue("valid-token")
            .header("alg", "none")
            .claim("sub", "user-123")
            .claim("org", "org-456")
            .claim("jti", "jti-789")
            .claim("role", "ORGANIZER")
            .claim("sid", "session-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .build();

        when(jwtDecoder.decode(anyString())).thenReturn(validJwt);

        mockMvc
            .perform(
                get("/api/v1/admin/dummy").header(
                    "Authorization",
                    "Bearer valid-token"
                )
            )
            .andExpect(status().isOk());
    }

    @Test
    void shouldReturn403_whenRoleIsStudent() throws Exception {
        Jwt studentJwt = Jwt.withTokenValue("student-token")
            .header("alg", "none")
            .claim("sub", "user-123")
            .claim("org", "org-456")
            .claim("jti", "jti-789")
            .claim("role", "STUDENT")
            .claim("sid", "session-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .build();

        when(jwtDecoder.decode(anyString())).thenReturn(studentJwt);

        mockMvc
            .perform(
                get("/api/v1/admin/dummy").header(
                    "Authorization",
                    "Bearer student-token"
                )
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn401_whenTokenIsRevoked() throws Exception {
        Jwt revokedJwt = Jwt.withTokenValue("revoked-token")
            .header("alg", "none")
            .claim("sub", "user-123")
            .claim("org", "org-456")
            .claim("jti", "revoked-jti")
            .claim("role", "ORGANIZER")
            .claim("sid", "session-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .build();

        when(jwtDecoder.decode(anyString())).thenReturn(revokedJwt);
        when(redisJtiValidator.isTokenValid("revoked-jti")).thenReturn(false);

        mockMvc
            .perform(
                get("/api/v1/admin/dummy").header(
                    "Authorization",
                    "Bearer revoked-token"
                )
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn503_whenRedisIsUnavailable() throws Exception {
        Jwt validJwt = Jwt.withTokenValue("valid-token")
            .header("alg", "none")
            .claim("sub", "user-123")
            .claim("org", "org-456")
            .claim("jti", "jti-789")
            .claim("role", "ORGANIZER")
            .claim("sid", "session-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
            .build();

        when(jwtDecoder.decode(anyString())).thenReturn(validJwt);
        doThrow(
            new AppException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "urn:unihub:error:service-unavailable",
                "Redis is down"
            )
        )
            .when(redisJtiValidator)
            .isTokenValid("jti-789");

        mockMvc
            .perform(
                get("/api/v1/admin/dummy").header(
                    "Authorization",
                    "Bearer valid-token"
                )
            )
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldReturn200_forPublicEndpointWithoutToken() throws Exception {
        mockMvc
            .perform(get("/api/v1/payments/webhook"))
            .andExpect(status().isOk());
    }
}
