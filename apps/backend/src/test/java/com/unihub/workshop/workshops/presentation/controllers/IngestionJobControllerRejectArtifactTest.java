package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.workshops.application.ports.in.GetRejectArtifactUrlUseCase;
import com.unihub.workshop.workshops.application.ports.in.ListIngestionJobsUseCase;
import com.unihub.workshop.workshops.application.usecases.RejectArtifactUrlResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
class IngestionJobControllerRejectArtifactTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ListIngestionJobsUseCase listUseCase;

    @MockitoBean
    private GetRejectArtifactUrlUseCase getRejectArtifactUrlUseCase;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private TestingAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
                UUID.randomUUID().toString(), ORG_ID.toString(), "ORGANIZER", "test@org.com", Instant.now(),
                Instant.now().plusSeconds(3600));
        auth = new TestingAuthenticationToken(principal, null, "ROLE_ORGANIZER");
    }

    @Test
    void getRejectArtifactUrl_Success() throws Exception {
        RejectArtifactUrlResult result = new RejectArtifactUrlResult(
                JOB_ID, "SOURCE_FILE", "https://minio/artifact", Instant.parse("2026-08-31T02:30:00Z"));

        when(getRejectArtifactUrlUseCase.execute(JOB_ID, ORG_ID)).thenReturn(result);

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/{jobId}/reject-artifact", JOB_ID.toString())
                .with(authentication(auth))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pre-signed download URL generated"))
                .andExpect(jsonPath("$.data.jobId").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.artifactType").value("SOURCE_FILE"))
                .andExpect(jsonPath("$.data.downloadUrl").value("https://minio/artifact"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-08-31T02:30:00Z"));
    }

    @Test
    void getRejectArtifactUrl_InvalidUuidFormat_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/not-a-uuid/reject-artifact")
                .with(authentication(auth))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:bad-request"));
    }

    @Test
    void getRejectArtifactUrl_NotFound_Returns404() throws Exception {
        when(getRejectArtifactUrlUseCase.execute(JOB_ID, ORG_ID))
                .thenThrow(new AppException(org.springframework.http.HttpStatus.NOT_FOUND, "urn:unihub:error:not-found",
                        "IngestionJobReport not found"));

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/{jobId}/reject-artifact", JOB_ID.toString())
                .with(authentication(auth))
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:not-found"));
    }

    @Test
    void getRejectArtifactUrl_MinioError_Returns500() throws Exception {
        when(getRejectArtifactUrlUseCase.execute(JOB_ID, ORG_ID))
                .thenThrow(new RuntimeException("MinIO error"));

        mockMvc.perform(get("/api/v1/admin/ingestion-jobs/{jobId}/reject-artifact", JOB_ID.toString())
                .with(authentication(auth))
                .with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.type").value("urn:unihub:error:internal"));
    }
}
