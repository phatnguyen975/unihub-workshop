package com.unihub.workshop.attendances.presentation.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesUseCase;
import com.unihub.workshop.attendances.domain.model.AttendanceClassification;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncEntryRequest;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncOutcomeResponse;
import com.unihub.workshop.attendances.presentation.dto.AttendanceSyncRequest;
import com.unihub.workshop.attendances.presentation.mapper.AttendancePresentationMapper;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AttendanceSyncControllerTest {

    private static final UUID WORKSHOP_ID = UUID.fromString(
        "f0000001-0000-4000-a000-000000000001"
    );
    private static final UUID STAFF_ID = UUID.fromString(
        "a0000001-0000-4000-a000-000000000001"
    );
    private static final UUID FIRST_SYNC_ID = UUID.fromString(
        "c0000001-0000-4000-a000-000000000001"
    );
    private static final UUID SECOND_SYNC_ID = UUID.fromString(
        "c0000001-0000-4000-a000-000000000002"
    );
    private static final UUID THIRD_SYNC_ID = UUID.fromString(
        "c0000001-0000-4000-a000-000000000003"
    );
    private static final String SYNC_PATH =
        "/api/v1/workshops/" + WORKSHOP_ID + "/attendances/sync";

    private MockMvc mockMvc;
    private SyncAttendancesUseCase useCase;
    private AttendancePresentationMapper presentationMapper;

    @BeforeEach
    void setUp() {
        useCase = mock(SyncAttendancesUseCase.class);
        presentationMapper = mock(AttendancePresentationMapper.class);

        AttendanceSyncController controller = new AttendanceSyncController(
            useCase,
            presentationMapper
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private static TestingAuthenticationToken staffAuth(UUID userId) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "STAFF",
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(
            principal,
            "mock-jwt",
            "ROLE_STAFF"
        );
    }

    private static TestingAuthenticationToken nonStaffAuth(String role) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            STAFF_ID.toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            role,
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(
            principal,
            "mock-jwt",
            "ROLE_" + role
        );
    }

    private static String requestBody(int entryCount) {
        StringBuilder body = new StringBuilder(
            "{\"deviceId\":\"device-abc-123\",\"entries\":["
        );
        for (int index = 0; index < entryCount; index++) {
            if (index > 0) {
                body.append(',');
            }
            body.append("{\"syncId\":\"")
                .append(UUID.randomUUID())
                .append("\",\"registrationId\":\"")
                .append(UUID.randomUUID())
                .append("\",\"scannedAt\":\"2026-11-15T09:30:00Z\"}");
        }
        body.append("]}");
        return body.toString();
    }

    @Test
    @DisplayName(
        "POST sync returns 207 with one outcome per entry in submitted order"
    )
    void sync_returnsMultiStatusWithOneOutcomePerEntry() throws Exception {
        List<AttendanceOutcome> outcomes = List.of(
            new AttendanceOutcome(
                FIRST_SYNC_ID,
                AttendanceClassification.SUCCESS
            ),
            new AttendanceOutcome(
                SECOND_SYNC_ID,
                AttendanceClassification.ALREADY_USED
            ),
            new AttendanceOutcome(
                THIRD_SYNC_ID,
                AttendanceClassification.INVALID_TICKET
            )
        );
        List<AttendanceSyncOutcomeResponse> responses = List.of(
            new AttendanceSyncOutcomeResponse(FIRST_SYNC_ID, "SUCCESS"),
            new AttendanceSyncOutcomeResponse(SECOND_SYNC_ID, "ALREADY_USED"),
            new AttendanceSyncOutcomeResponse(THIRD_SYNC_ID, "INVALID_TICKET")
        );

        when(useCase.execute(any(SyncAttendancesCommand.class))).thenReturn(
            outcomes
        );
        when(presentationMapper.toCommand(any(), any(), any())).thenReturn(
            new SyncAttendancesCommand(
                WORKSHOP_ID,
                STAFF_ID,
                "device-abc-123",
                List.of()
            )
        );
        when(presentationMapper.toResponses(outcomes)).thenReturn(responses);

        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(3))
            )
            .andExpect(status().isMultiStatus())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Attendance sync processed"))
            .andExpect(
                jsonPath("$.data[0].syncId").value(FIRST_SYNC_ID.toString())
            )
            .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
            .andExpect(
                jsonPath("$.data[1].syncId").value(SECOND_SYNC_ID.toString())
            )
            .andExpect(jsonPath("$.data[1].status").value("ALREADY_USED"))
            .andExpect(
                jsonPath("$.data[2].syncId").value(THIRD_SYNC_ID.toString())
            )
            .andExpect(jsonPath("$.data[2].status").value("INVALID_TICKET"))
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.metadata.processedBy").isNotEmpty())
            .andExpect(jsonPath("$.metadata.traceId").isNotEmpty());
    }

    @Test
    @DisplayName(
        "POST sync forwards the path workshop, the JWT subject and the device id to the use case"
    )
    void sync_forwardsWorkshopCallerAndDeviceToUseCase() throws Exception {
        when(useCase.execute(any(SyncAttendancesCommand.class))).thenReturn(
            List.of()
        );
        when(presentationMapper.toCommand(any(), any(), any())).thenReturn(
            new SyncAttendancesCommand(
                WORKSHOP_ID,
                STAFF_ID,
                "device-abc-123",
                List.of()
            )
        );
        when(presentationMapper.toResponses(List.of())).thenReturn(List.of());

        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(1))
            )
            .andExpect(status().isMultiStatus());

        ArgumentCaptor<AttendanceSyncRequest> requestCaptor =
            ArgumentCaptor.forClass(AttendanceSyncRequest.class);
        ArgumentCaptor<UUID> workshopCaptor = ArgumentCaptor.forClass(
            UUID.class
        );
        ArgumentCaptor<UUID> callerCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(presentationMapper).toCommand(
            requestCaptor.capture(),
            workshopCaptor.capture(),
            callerCaptor.capture()
        );

        assertThat(workshopCaptor.getValue()).isEqualTo(WORKSHOP_ID);
        assertThat(callerCaptor.getValue()).isEqualTo(STAFF_ID);
        assertThat(requestCaptor.getValue().deviceId()).isEqualTo(
            "device-abc-123"
        );
        assertThat(requestCaptor.getValue().entries()).hasSize(1);
        AttendanceSyncEntryRequest entry = requestCaptor
            .getValue()
            .entries()
            .get(0);
        assertThat(entry.syncId()).isNotNull();
        assertThat(entry.registrationId()).isNotNull();
        assertThat(entry.scannedAt()).isNotNull();
    }

    @Test
    @DisplayName("POST sync returns 401 when no authentication is supplied")
    void sync_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc
            .perform(
                post(SYNC_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(1))
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );

        verify(useCase, never()).execute(any());
    }

    @Test
    @DisplayName("POST sync returns 403 when the caller is not STAFF")
    void sync_withNonStaffRole_returnsForbidden() throws Exception {
        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(nonStaffAuth("STUDENT"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(1))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );

        verify(useCase, never()).execute(any());
    }

    @Test
    @DisplayName(
        "POST sync returns 400 when the workshop path segment is not a UUID"
    )
    void sync_malformedWorkshopId_returnsBadRequest() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/workshops/not-a-uuid/attendances/sync")
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(1))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));

        verify(useCase, never()).execute(any());
    }

    @Test
    @DisplayName("POST sync returns 404 when the workshop does not exist")
    void sync_unknownWorkshop_returnsNotFound() throws Exception {
        when(useCase.execute(any(SyncAttendancesCommand.class))).thenThrow(
            new NotFoundException("Workshop not found: " + WORKSHOP_ID)
        );
        when(presentationMapper.toCommand(any(), any(), any())).thenReturn(
            new SyncAttendancesCommand(WORKSHOP_ID, STAFF_ID, null, List.of())
        );

        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(1))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "POST sync returns 403 when the caller is not assigned to the workshop"
    )
    void sync_unassignedStaff_returnsForbidden() throws Exception {
        when(useCase.execute(any(SyncAttendancesCommand.class))).thenThrow(
            new ForbiddenException(
                "Staff member is not assigned to this workshop"
            )
        );
        when(presentationMapper.toCommand(any(), any(), any())).thenReturn(
            new SyncAttendancesCommand(WORKSHOP_ID, STAFF_ID, null, List.of())
        );

        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(1))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    @DisplayName(
        "POST sync returns 422 when the workshop is not open for attendance sync"
    )
    void sync_ineligibleWorkshop_returnsValidationError() throws Exception {
        when(useCase.execute(any(SyncAttendancesCommand.class))).thenThrow(
            new ValidationException("workshopId", "Workshop is DRAFT")
        );
        when(presentationMapper.toCommand(any(), any(), any())).thenReturn(
            new SyncAttendancesCommand(WORKSHOP_ID, STAFF_ID, null, List.of())
        );

        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(1))
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(jsonPath("$.error.validationErrors").isArray());
    }

    @Test
    @DisplayName("POST sync returns 422 when the entries array is empty")
    void sync_emptyEntries_returnsValidationErrorWithoutCallingUseCase()
        throws Exception {
        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"deviceId\":\"device-abc-123\",\"entries\":[]}")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.validationErrors").isArray());

        verify(useCase, never()).execute(any());
        verify(presentationMapper, never()).toCommand(any(), any(), any());
    }

    @Test
    @DisplayName("POST sync returns 422 when the entries array is absent")
    void sync_absentEntries_returnsValidationError() throws Exception {
        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"deviceId\":\"device-abc-123\"}")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false));

        verify(useCase, never()).execute(any());
    }

    @Test
    @DisplayName("POST sync returns 422 when an entry field is missing")
    void sync_missingEntryField_returnsValidationError() throws Exception {
        String body =
            "{\"entries\":[{\"registrationId\":\"" +
            UUID.randomUUID() +
            "\",\"scannedAt\":\"2026-11-15T09:30:00Z\"}]}";

        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.validationErrors").isArray());

        verify(useCase, never()).execute(any());
    }

    @Test
    @DisplayName("POST sync returns 422 when an entry is null")
    void sync_nullEntry_returnsValidationError() throws Exception {
        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entries\":[null]}")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false));

        verify(useCase, never()).execute(any());
    }

    @Test
    @DisplayName("POST sync returns 400 when the JSON body is malformed")
    void sync_malformedJson_returnsBadRequest() throws Exception {
        mockMvc
            .perform(
                post(SYNC_PATH)
                    .principal(staffAuth(STAFF_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entries\":[")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));

        verify(useCase, never()).execute(any());
    }
}
