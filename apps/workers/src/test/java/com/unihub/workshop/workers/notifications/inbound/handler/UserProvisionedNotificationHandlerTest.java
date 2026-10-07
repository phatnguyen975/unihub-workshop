package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.UserProvisionedEventDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProvisionedNotificationHandlerTest {

    @Mock
    private MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    private UserProvisionedNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserProvisionedNotificationHandler(deliveryUseCase);
    }

    @Test
    @DisplayName("Should return exact supported event type")
    void shouldReturnSupportedEventType() {
        assertEquals("UserProvisionedEvent", handler.supportedEventType());
        assertEquals(UserProvisionedEventDto.class, handler.eventDtoClass());
    }

    @Test
    @DisplayName(
        "Should handle UserProvisionedEvent: In-App (action_url=null, NO temp pass in payload), Email, and NO SSE"
    )
    void shouldHandleUserProvisionedEvent() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        UserProvisionedEventDto dto = new UserProvisionedEventDto(
            userId,
            orgId,
            "fit-it",
            "organizer@hcmus.edu.vn",
            "Nguyen Van B",
            "ORGANIZER",
            "TempSecret123!",
            Instant.now()
        );

        handler.handle(dto);

        verify(deliveryUseCase).deliverMultiChannel(
            argThat(
                cmd ->
                    cmd.userId().equals(userId) &&
                    "organizer@hcmus.edu.vn".equals(cmd.recipientEmail()) &&
                    cmd.sendInApp() &&
                    "USER_PROVISIONED".equals(cmd.inAppEventKey()) &&
                    cmd.inAppActionUrl() == null && // action_url = null
                    !cmd.inAppPayload().containsKey("temporaryPassword") && // NEVER in payload
                    !cmd.sendRealtime() && // NO SSE for UserProvisionedEvent
                    cmd.sendEmail() &&
                    "user-provisioned".equals(cmd.emailTemplateName()) &&
                    "TempSecret123!".equals(
                        cmd.emailTemplateModel().get("temporaryPassword")
                    ) &&
                    cmd.idempotencyKey().equals("USER_PROVISIONED:" + userId)
            )
        );
    }

    @Test
    @DisplayName("Should skip when event or userId is null")
    void shouldSkipWhenInvalid() {
        handler.handle(null);
        handler.handle(
            new UserProvisionedEventDto(
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
