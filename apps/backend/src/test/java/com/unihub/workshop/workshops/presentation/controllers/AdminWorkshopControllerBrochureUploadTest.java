package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.BrochureUploadResult;
import com.unihub.workshop.workshops.application.ports.in.UploadBrochureCommand;
import com.unihub.workshop.workshops.application.ports.in.UploadBrochureUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerBrochureUploadTest {

    private MockMvc mockMvc;
    private UploadBrochureUseCase uploadBrochureUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final byte[] VALID_PDF_BYTES =
        "%PDF-1.4 test brochure content".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        uploadBrochureUseCase = mock(UploadBrochureUseCase.class);

        AdminWorkshopController controller =
            AdminWorkshopControllerTestBuilder.builder()
                .withUploadBrochureUseCase(uploadBrochureUseCase)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken organizerAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            CALLER_ID.toString(),
            ORG_ID.toString(),
            "jti-123",
            "ORGANIZER",
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(
            principal,
            null,
            "ROLE_ORGANIZER"
        );
    }

    @Test
    @DisplayName(
        "POST /api/v1/admin/workshops/{id}/brochure should return 202 Accepted with job info on success"
    )
    void uploadBrochureSuccess() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID brochureId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-12T00:00:00Z");

        when(
            uploadBrochureUseCase.uploadBrochure(
                any(UploadBrochureCommand.class)
            )
        ).thenReturn(
            new BrochureUploadResult(jobId, brochureId, "PROCESSING", createdAt)
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "brochure.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            VALID_PDF_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/{id}/brochure", WORKSHOP_ID)
                    .file(file)
                    .principal(organizerAuth())
            )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value(
                    "Brochure upload accepted. AI summarization is processing."
                )
            )
            .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
            .andExpect(
                jsonPath("$.data.brochureId").value(brochureId.toString())
            )
            .andExpect(jsonPath("$.data.status").value("PROCESSING"))
            .andExpect(
                jsonPath("$.data.createdAt").value("2026-10-12T00:00:00Z")
            );

        verify(uploadBrochureUseCase).uploadBrochure(
            any(UploadBrochureCommand.class)
        );
    }

    @Test
    @DisplayName(
        "POST /api/v1/admin/workshops/{id}/brochure should return 422 when file part is empty"
    )
    void uploadBrochureEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "empty.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            new byte[0]
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/{id}/brochure", WORKSHOP_ID)
                    .file(file)
                    .principal(organizerAuth())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/admin/workshops/{id}/brochure should return 404 when workshop does not exist"
    )
    void uploadBrochureWorkshopNotFound() throws Exception {
        when(
            uploadBrochureUseCase.uploadBrochure(
                any(UploadBrochureCommand.class)
            )
        ).thenThrow(new NotFoundException("Workshop not found"));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "brochure.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            VALID_PDF_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/{id}/brochure", WORKSHOP_ID)
                    .file(file)
                    .principal(organizerAuth())
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/admin/workshops/{id}/brochure should return 403 when organization does not match"
    )
    void uploadBrochureForbidden() throws Exception {
        when(
            uploadBrochureUseCase.uploadBrochure(
                any(UploadBrochureCommand.class)
            )
        ).thenThrow(new ForbiddenException("You do not have permission"));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "brochure.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            VALID_PDF_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/{id}/brochure", WORKSHOP_ID)
                    .file(file)
                    .principal(organizerAuth())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/admin/workshops/{id}/brochure should return 409 when processing job already exists"
    )
    void uploadBrochureConflict() throws Exception {
        when(
            uploadBrochureUseCase.uploadBrochure(
                any(UploadBrochureCommand.class)
            )
        ).thenThrow(
            new ConflictException(
                "An AI summary job is currently in PROCESSING status"
            )
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "brochure.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            VALID_PDF_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/{id}/brochure", WORKSHOP_ID)
                    .file(file)
                    .principal(organizerAuth())
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    @DisplayName(
        "POST /api/v1/admin/workshops/{id}/brochure should return 422 on validation failure (bad magic bytes, etc.)"
    )
    void uploadBrochureValidationFailure() throws Exception {
        when(
            uploadBrochureUseCase.uploadBrochure(
                any(UploadBrochureCommand.class)
            )
        ).thenThrow(
            new ValidationException(
                "file",
                "File does not contain valid PDF header magic bytes %PDF-"
            )
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "brochure.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            VALID_PDF_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/{id}/brochure", WORKSHOP_ID)
                    .file(file)
                    .principal(organizerAuth())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }
}
