package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.storage.MediaAssetType;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.shared.storage.StoredMedia;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftUseCase;
import com.unihub.workshop.workshops.presentation.mapper.WorkshopPresentationMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminWorkshopControllerMediaTest {

    private MockMvc mockMvc;
    private MediaStoragePort mediaStoragePort;
    private SaveDraftUseCase saveDraftUseCase;

    private static final UUID CALLER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    private static final byte[] VALID_JPEG_BYTES = new byte[] {
        (byte) 0xFF,
        (byte) 0xD8,
        (byte) 0xFF,
        (byte) 0xE0,
        0x00,
        0x10,
        0x4A,
        0x46,
        0x49,
        0x46,
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        (byte) 0xFF,
        (byte) 0xD9,
    };

    private static final byte[] VALID_PNG_BYTES = new byte[] {
        (byte) 0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
        0x49,
        0x48,
        0x44,
        0x52,
    };

    @BeforeEach
    void setUp() {
        saveDraftUseCase = mock(SaveDraftUseCase.class);
        mediaStoragePort = mock(MediaStoragePort.class);
        WorkshopPresentationMapper mapper = Mappers.getMapper(
            WorkshopPresentationMapper.class
        );

        AdminWorkshopController controller = new AdminWorkshopController(
            saveDraftUseCase,
            mapper,
            mediaStoragePort
        );

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

    private TestingAuthenticationToken studentAuth() {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            CALLER_ID.toString(),
            null,
            "jti-123",
            "STUDENT",
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_STUDENT");
    }

    @Test
    @DisplayName("Upload valid cover image returns 201 Created")
    void uploadMedia_withValidCoverImage_returns201Created() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "banner.jpg",
            "image/jpeg",
            VALID_JPEG_BYTES
        );

        StoredMedia storedMedia = StoredMedia.builder()
            .fileKey("workshops/covers/test-cover.jpg")
            .publicUrl(
                "http://localhost:9000/unihub-media/workshops/covers/test-cover.jpg"
            )
            .assetType(MediaAssetType.COVER_IMAGE)
            .size((long) VALID_JPEG_BYTES.length)
            .contentType("image/jpeg")
            .build();

        when(
            mediaStoragePort.upload(
                any(InputStream.class),
                eq((long) VALID_JPEG_BYTES.length),
                eq("image/jpeg"),
                eq(MediaAssetType.COVER_IMAGE),
                eq(".jpg")
            )
        ).thenReturn(storedMedia);

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(file)
                    .param("type", "COVER_IMAGE")
                    .principal(organizerAuth())
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message").value("Media uploaded successfully.")
            )
            .andExpect(
                jsonPath("$.data.url").value(
                    "http://localhost:9000/unihub-media/workshops/covers/test-cover.jpg"
                )
            )
            .andExpect(
                jsonPath("$.data.fileKey").value(
                    "workshops/covers/test-cover.jpg"
                )
            )
            .andExpect(jsonPath("$.data.type").value("COVER_IMAGE"))
            .andExpect(jsonPath("$.data.size").value(VALID_JPEG_BYTES.length))
            .andExpect(jsonPath("$.data.contentType").value("image/jpeg"));

        verify(mediaStoragePort).upload(
            any(InputStream.class),
            eq((long) VALID_JPEG_BYTES.length),
            eq("image/jpeg"),
            eq(MediaAssetType.COVER_IMAGE),
            eq(".jpg")
        );
    }

    @Test
    @DisplayName("Upload valid speaker avatar returns 201 Created")
    void uploadMedia_withValidSpeakerAvatar_returns201Created()
        throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "speaker.png",
            "image/png",
            VALID_PNG_BYTES
        );

        StoredMedia storedMedia = StoredMedia.builder()
            .fileKey("speakers/avatars/test-speaker.png")
            .publicUrl(
                "http://localhost:9000/unihub-media/speakers/avatars/test-speaker.png"
            )
            .assetType(MediaAssetType.SPEAKER_AVATAR)
            .size((long) VALID_PNG_BYTES.length)
            .contentType("image/png")
            .build();

        when(
            mediaStoragePort.upload(
                any(InputStream.class),
                eq((long) VALID_PNG_BYTES.length),
                eq("image/png"),
                eq(MediaAssetType.SPEAKER_AVATAR),
                eq(".png")
            )
        ).thenReturn(storedMedia);

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(file)
                    .param("type", "SPEAKER_AVATAR")
                    .principal(organizerAuth())
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data.url").value(
                    "http://localhost:9000/unihub-media/speakers/avatars/test-speaker.png"
                )
            )
            .andExpect(
                jsonPath("$.data.fileKey").value(
                    "speakers/avatars/test-speaker.png"
                )
            )
            .andExpect(jsonPath("$.data.type").value("SPEAKER_AVATAR"));
    }

    @Test
    @DisplayName("Upload with invalid type returns 400 Bad Request")
    void uploadMedia_withInvalidType_returns400BadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "banner.jpg",
            "image/jpeg",
            VALID_JPEG_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(file)
                    .param("type", "USER_AVATAR")
                    .principal(organizerAuth())
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    @DisplayName("Upload with empty type returns 400 Bad Request")
    void uploadMedia_withEmptyType_returns400BadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "banner.jpg",
            "image/jpeg",
            VALID_JPEG_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(file)
                    .param("type", "   ")
                    .principal(organizerAuth())
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    @DisplayName("Upload with missing type parameter returns 400 Bad Request")
    void uploadMedia_withMissingType_returns400BadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "banner.jpg",
            "image/jpeg",
            VALID_JPEG_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(file)
                    .principal(organizerAuth())
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    @DisplayName("Upload with empty file returns 422 Unprocessable Content")
    void uploadMedia_withEmptyFile_returns422UnprocessableContent()
        throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file",
            "empty.jpg",
            "image/jpeg",
            new byte[0]
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(emptyFile)
                    .param("type", "COVER_IMAGE")
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
        "Upload with file exceeding 5MB returns 422 Unprocessable Content"
    )
    void uploadMedia_withOversizedFile_returns422UnprocessableContent()
        throws Exception {
        byte[] oversizedBytes = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(
            VALID_JPEG_BYTES,
            0,
            oversizedBytes,
            0,
            VALID_JPEG_BYTES.length
        );

        MockMultipartFile oversizedFile = new MockMultipartFile(
            "file",
            "big.jpg",
            "image/jpeg",
            oversizedBytes
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(oversizedFile)
                    .param("type", "COVER_IMAGE")
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
        "Upload with unsupported MIME type returns 422 Unprocessable Content"
    )
    void uploadMedia_withUnsupportedMime_returns422UnprocessableContent()
        throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
            "file",
            "brochure.pdf",
            "application/pdf",
            new byte[] { 0x25, 0x50, 0x44, 0x46 }
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(pdfFile)
                    .param("type", "COVER_IMAGE")
                    .principal(organizerAuth())
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName("Upload when storage fails throws 500 Internal Server Error")
    void uploadMedia_whenStorageFails_returns500InternalServerError()
        throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "banner.jpg",
            "image/jpeg",
            VALID_JPEG_BYTES
        );

        when(
            mediaStoragePort.upload(
                any(),
                anyLong(),
                anyString(),
                any(),
                anyString()
            )
        ).thenThrow(
            new InternalServerException(
                "Storage provider error during MinIO upload"
            )
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(file)
                    .param("type", "COVER_IMAGE")
                    .principal(organizerAuth())
            )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:internal")
            );
    }

    @Test
    @DisplayName("Upload when caller is not an ORGANIZER returns 403 Forbidden")
    void uploadMedia_whenCallerNotOrganizer_returns403Forbidden()
        throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "banner.jpg",
            "image/jpeg",
            VALID_JPEG_BYTES
        );

        mockMvc
            .perform(
                multipart("/api/v1/admin/workshops/media/upload")
                    .file(file)
                    .param("type", "COVER_IMAGE")
                    .principal(studentAuth())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }
}
