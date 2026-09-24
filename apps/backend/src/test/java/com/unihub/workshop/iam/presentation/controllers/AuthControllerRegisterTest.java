package com.unihub.workshop.iam.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.iam.application.ports.in.RegisterUserUseCase;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerRegisterTest {

    private MockMvc mockMvc;
    private RegisterUserUseCase registerUserUseCase;

    @BeforeEach
    void setUp() {
        registerUserUseCase = mock(RegisterUserUseCase.class);
        AuthController authController = AuthControllerTestBuilder.builder()
            .withRegisterUserUseCase(registerUserUseCase)
            .build();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    @Test
    void shouldReturn201OnSuccessfulRegistration() throws Exception {
        UUID userId = UUID.randomUUID();
        when(
            registerUserUseCase.register(
                "alice@university.edu",
                "Str0ng!Pass",
                "Alice Smith"
            )
        ).thenReturn(userId);

        String requestBody = """
        {
            "email": "alice@university.edu",
            "password": "Str0ng!Pass",
            "fullName": "Alice Smith"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(userId.toString()))
            .andExpect(
                jsonPath("$.message").value(
                    "Registration successful. Please check your email for the activation code."
                )
            );
    }

    @Test
    void shouldReturn409OnDuplicateEmail() throws Exception {
        when(registerUserUseCase.register(any(), any(), any())).thenThrow(
            new ConflictException("An account with this email already exists.")
        );

        String requestBody = """
        {
            "email": "existing@university.edu",
            "password": "Str0ng!Pass",
            "fullName": "Alice Smith"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    void shouldReturn422OnInvalidEmailFormat() throws Exception {
        String requestBody = """
        {
            "email": "not-an-email",
            "password": "Str0ng!Pass",
            "fullName": "Alice Smith"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());
    }

    @Test
    void shouldReturn422OnWeakPassword() throws Exception {
        String requestBody = """
        {
            "email": "alice@university.edu",
            "password": "password",
            "fullName": "Alice Smith"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors[0]").exists());
    }

    @Test
    void shouldReturn422WhenFullNameIsMissing() throws Exception {
        String requestBody = """
        {
            "email": "alice@university.edu",
            "password": "Str0ng!Pass"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath(
                    "$.error.validationErrors[?(@ =~ /fullName: .+/)]"
                ).exists()
            );
    }

    @Test
    void shouldReturn422WhenFullNameIsBlank() throws Exception {
        String requestBody = """
        {
            "email": "alice@university.edu",
            "password": "Str0ng!Pass",
            "fullName": "   "
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath(
                    "$.error.validationErrors[?(@ =~ /fullName: .+/)]"
                ).exists()
            );
    }

    @Test
    void shouldReturn422WhenEmailExceedsMaxLength() throws Exception {
        String longEmail = "a".repeat(245) + "@domain.edu"; // 256 chars > 254
        String requestBody = String.format(
            """
            {
                "email": "%s",
                "password": "Str0ng!Pass",
                "fullName": "Alice Smith"
            }
            """,
            longEmail
        );

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath(
                    "$.error.validationErrors[?(@ =~ /email: .+/)]"
                ).exists()
            );
    }

    @Test
    void shouldReturn422WhenPasswordIsTooShort() throws Exception {
        String requestBody = """
        {
            "email": "alice@university.edu",
            "password": "S!1a",
            "fullName": "Alice Smith"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    void shouldReturn422WhenPasswordMissingSpecialCharacter() throws Exception {
        String requestBody = """
        {
            "email": "alice@university.edu",
            "password": "Str0ngPassword123",
            "fullName": "Alice Smith"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    void shouldReturn400OnMalformedJsonBody() throws Exception {
        String malformedJson = "{ \"email\": \"alice@university.edu\", ";

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldContainMetadataInSuccessResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        when(registerUserUseCase.register(any(), any(), any())).thenReturn(
            userId
        );

        String requestBody = """
        {
            "email": "alice@university.edu",
            "password": "Str0ng!Pass",
            "fullName": "Alice Smith"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.metadata").exists());
    }

    @Test
    void shouldPassRawEmailToUseCaseWithoutModifyingCase() throws Exception {
        UUID userId = UUID.randomUUID();
        when(
            registerUserUseCase.register(
                "ALICE@UNIVERSITY.EDU",
                "Str0ng!Pass",
                "Alice Smith"
            )
        ).thenReturn(userId);

        String requestBody = """
        {
            "email": "ALICE@UNIVERSITY.EDU",
            "password": "Str0ng!Pass",
            "fullName": "Alice Smith"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.userId").value(userId.toString()));

        verify(registerUserUseCase).register(
            "ALICE@UNIVERSITY.EDU",
            "Str0ng!Pass",
            "Alice Smith"
        );
    }

    @Test
    void shouldReturn409WhenDataIntegrityViolationOccurs() throws Exception {
        when(registerUserUseCase.register(any(), any(), any())).thenThrow(
            new DataIntegrityViolationException(
                "duplicate key value violates unique constraint"
            )
        );

        String requestBody = """
        {
            "email": "existing@university.edu",
            "password": "Str0ng!Pass",
            "fullName": "Alice Smith"
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            );
    }

    @Test
    void shouldTrimWhitespaceFromEmailAndFullNameOnRegister() throws Exception {
        UUID userId = UUID.randomUUID();
        when(
            registerUserUseCase.register(
                "alice@university.edu",
                "Str0ng!Pass",
                "Alice Smith"
            )
        ).thenReturn(userId);

        String requestBody = """
        {
            "email": "   alice@university.edu   ",
            "password": "Str0ng!Pass",
            "fullName": "   Alice Smith   "
        }
        """;

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(userId.toString()));

        verify(registerUserUseCase).register(
            "alice@university.edu",
            "Str0ng!Pass",
            "Alice Smith"
        );
    }

    @Test
    void shouldSerializeResponseInExactOrderSpecifiedByApiStandards()
        throws Exception {
        UUID userId = UUID.randomUUID();
        when(registerUserUseCase.register(any(), any(), any())).thenReturn(
            userId
        );

        String requestBody = """
        {
            "email": "alice@university.edu",
            "password": "Str0ng!Pass",
            "fullName": "Alice Smith"
        }
        """;

        String jsonResponse = mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        int successIdx = jsonResponse.indexOf("\"success\"");
        int messageIdx = jsonResponse.indexOf("\"message\"");
        int dataIdx = jsonResponse.indexOf("\"data\"");
        int metadataIdx = jsonResponse.indexOf("\"metadata\"");

        assertThat(successIdx).isGreaterThanOrEqualTo(0);
        assertThat(messageIdx).isGreaterThan(successIdx);
        assertThat(dataIdx).isGreaterThan(messageIdx);
        assertThat(metadataIdx).isGreaterThan(dataIdx);
    }
}
