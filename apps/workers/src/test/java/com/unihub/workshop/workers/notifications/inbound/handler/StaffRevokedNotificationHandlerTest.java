package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.StaffRevokedEventDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaffRevokedNotificationHandlerTest {

    @Mock
    private MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    private StaffRevokedNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StaffRevokedNotificationHandler(deliveryUseCase);
    }

    @Test
    @DisplayName("Should return exact supported event type")
    void shouldReturnSupportedEventType() {
        assertEquals(
            "StaffRevokedFromWorkshopEvent",
            handler.supportedEventType()
        );
        assertEquals(StaffRevokedEventDto.class, handler.eventDtoClass());
    }

    @Test
    @DisplayName(
        "Should deliver across In-App, Real-Time SSE, and Email for StaffRevokedFromWorkshopEvent with action_url=/workshops"
    )
    void shouldHandleStaffRevokedEvent() {
        UUID staffUserId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID revokedBy = UUID.randomUUID();

        StaffRevokedEventDto dto = new StaffRevokedEventDto(
            workshopId,
            staffUserId,
            orgId,
            revokedBy,
            "Cloud Computing",
            null,
            null,
            null,
            "staff@hcmus.edu.vn",
            Instant.now()
        );

        handler.handle(dto);

        verify(deliveryUseCase).deliverMultiChannel(
            argThat(
                cmd ->
                    cmd.userId().equals(staffUserId) &&
                    cmd.sendInApp() &&
                    "STAFF_REVOKED_FROM_WORKSHOP".equals(cmd.inAppEventKey()) &&
                    "/workshops".equals(cmd.inAppActionUrl()) &&
                    cmd.sendRealtime() &&
                    cmd.sendEmail() &&
                    "staff-revoked".equals(cmd.emailTemplateName()) &&
                    cmd
                        .idempotencyKey()
                        .equals(
                            "STAFF_REVOKED:" + staffUserId + ":" + workshopId
                        )
            )
        );
    }

    @Test
    @DisplayName("Should skip when event or staffUserId is null")
    void shouldSkipWhenInvalid() {
        handler.handle(null);
        handler.handle(
            new StaffRevokedEventDto(
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
