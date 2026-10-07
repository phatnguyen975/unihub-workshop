package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.StaffAssignedEventDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaffAssignedNotificationHandlerTest {

    @Mock
    private MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    private StaffAssignedNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StaffAssignedNotificationHandler(deliveryUseCase);
    }

    @Test
    @DisplayName("Should return exact supported event type")
    void shouldReturnSupportedEventType() {
        assertEquals(
            "StaffAssignedToWorkshopEvent",
            handler.supportedEventType()
        );
        assertEquals(StaffAssignedEventDto.class, handler.eventDtoClass());
    }

    @Test
    @DisplayName(
        "Should deliver across In-App, Real-Time SSE, and Email for StaffAssignedToWorkshopEvent with action_url"
    )
    void shouldHandleStaffAssignedEvent() {
        UUID staffUserId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID assignedBy = UUID.randomUUID();
        Instant startsAt = Instant.now();
        Instant endsAt = startsAt.plusSeconds(7200);

        StaffAssignedEventDto dto = new StaffAssignedEventDto(
            assignmentId,
            workshopId,
            staffUserId,
            orgId,
            assignedBy,
            "Cloud Computing",
            startsAt,
            endsAt,
            "Room 101",
            "staff@hcmus.edu.vn",
            Instant.now()
        );

        handler.handle(dto);

        verify(deliveryUseCase).deliverMultiChannel(
            argThat(
                cmd ->
                    cmd.userId().equals(staffUserId) &&
                    "staff@hcmus.edu.vn".equals(cmd.recipientEmail()) &&
                    cmd.sendInApp() &&
                    "STAFF_ASSIGNED_TO_WORKSHOP".equals(cmd.inAppEventKey()) &&
                    ("/workshops/" + workshopId).equals(cmd.inAppActionUrl()) &&
                    endsAt
                        .toString()
                        .equals(cmd.inAppPayload().get("endsAt")) &&
                    cmd.sendRealtime() &&
                    "notification.new".equals(cmd.realtimeEventType()) &&
                    cmd.sendEmail() &&
                    "staff-assigned".equals(cmd.emailTemplateName()) &&
                    endsAt
                        .toString()
                        .equals(cmd.emailTemplateModel().get("endsAt")) &&
                    cmd
                        .idempotencyKey()
                        .equals(
                            "STAFF_ASSIGNED:" + staffUserId + ":" + workshopId
                        )
            )
        );
    }

    @Test
    @DisplayName("Should skip when event is null or staffUserId is null")
    void shouldSkipWhenInvalid() {
        handler.handle(null);
        handler.handle(
            new StaffAssignedEventDto(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        verify(deliveryUseCase, never()).deliverMultiChannel(any());
    }
}
