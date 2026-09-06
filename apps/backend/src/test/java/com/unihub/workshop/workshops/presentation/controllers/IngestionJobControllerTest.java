package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.security.RedisJtiValidator;
import com.unihub.workshop.workshops.application.ports.in.ListIngestionJobsUseCase;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobOutcome;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = com.unihub.workshop.WorkshopApplication.class, properties = {
        "spring.flyway.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "datasource.primary.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.replica.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.primary.driver-class-name=org.h2.Driver",
        "datasource.replica.driver-class-name=org.h2.Driver"
})
@ActiveProfiles("test")
class IngestionJobControllerTest {

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private ListIngestionJobsUseCase listUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RedisJtiValidator redisJtiValidator;

    @BeforeEach
    void setUp() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                        .springSecurity())
                .build();
        when(redisJtiValidator.isTokenValid(anyString())).thenReturn(true);
    }

    private Jwt createMockJwt(String role, UUID orgId) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", orgId.toString())
                .claim("jti", "jti-123")
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void getIngestionJobs_withValidOrganizer_returns200() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("ORGANIZER", orgId));

        IngestionJobReport report = new IngestionJobReport(
                UUID.randomUUID(), 1L, orgId, "file.csv", IngestionJobOutcome.COMPLETED,
                100, 0, null, null, Instant.now(), Instant.now(), Instant.now());
        CursorPage<IngestionJobReport> page = new CursorPage<>(List.of(report), "next-cursor", true);

        when(listUseCase.execute(eq(orgId), isNull(), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].id").value(report.id().toString()))
                .andExpect(jsonPath("$.data.items[0].artifactAvailable").value(false))
                .andExpect(jsonPath("$.data.cursor.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.data.cursor.hasMore").value(true));
    }

    @Test
    void getIngestionJobs_withInvalidSize_returns400() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("ORGANIZER", orgId));

        when(listUseCase.execute(eq(orgId), isNull(), eq(0)))
                .thenThrow(new BadRequestException("Invalid cursor"));

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs?size=0")
                .header("Authorization", "Bearer token"))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:bad-request"));
    }

    @Test
    void getIngestionJobs_whenNoMorePages_omitsNextCursor() throws Exception {
        UUID orgId = UUID.randomUUID();
        when(jwtDecoder.decode(anyString())).thenReturn(createMockJwt("ORGANIZER", orgId));

        IngestionJobReport report = new IngestionJobReport(
                UUID.randomUUID(), 1L, orgId, "file.csv", IngestionJobOutcome.COMPLETED,
                100, 0, null, null, Instant.now(), Instant.now(), Instant.now());
        CursorPage<IngestionJobReport> page = new CursorPage<>(List.of(report), null, false);

        when(listUseCase.execute(eq(orgId), isNull(), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cursor.hasMore").value(false))
                .andExpect(jsonPath("$.data.cursor.nextCursor").doesNotExist());
    }

    @Test
    void getIngestionJobs_withInvalidOrgClaim_returns403() throws Exception {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .claim("sub", "user-123")
                .claim("org", "not-a-uuid")
                .claim("jti", "jti-123")
                .claim("role", "ORGANIZER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:forbidden"));
    }
}
