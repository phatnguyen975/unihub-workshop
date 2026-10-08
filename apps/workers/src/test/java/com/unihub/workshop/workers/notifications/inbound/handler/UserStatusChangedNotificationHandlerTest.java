package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.UserStatusChangedEventDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserStatusChangedNotificationHandlerTest {

    @Mock
    private MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    private UserStatusChangedNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserStatusChangedNotificationHandler(deliveryUseCase);
    }

    @Test
    @DisplayName("Should return exact supported event type")
    void shouldReturnSupportedEventType() {
        assertEquals("UserStatusChangedEvent", handler.supportedEventType());
        assertEquals(UserStatusChangedEventDto.class, handler.eventDtoClass());
    }

    @Test
    @DisplayName(
        "Should handle UserStatusChangedEvent: In-App, Email, and NO SSE"
    )
    void shouldHandleUserStatusChangedEvent() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        UserStatusChangedEventDto dto = new UserStatusChangedEventDto(
            userId,
            orgId,
            "student@hcmus.edu.vn",
            "Tran Van C",
            true,
            Instant.now()
        );

        handler.handle(dto);

        verify(deliveryUseCase).deliverMultiChannel(
            argThat(
                cmd ->
                    cmd.userId().equals(userId) &&
                    cmd.sendInApp() &&
                    "USER_STATUS_CHANGED".equals(cmd.inAppEventKey()) &&
                    cmd.inAppActionUrl() == null &&
                    !cmd.sendRealtime() && // NO SSE
                    cmd.sendEmail() &&
                    "user-status-changed".equals(cmd.emailTemplateName()) &&
                    cmd
                        .idempotencyKey()
                        .equals("USER_STATUS_CHANGED:" + userId + ":ACTIVE")
            )
        );
    }

    @Test
    @DisplayName("Should skip when event or userId is null")
    void shouldSkipWhenInvalid() {
        handler.handle(null);
        handler.handle(
            new UserStatusChangedEventDto(null, null, null, null, null, null)
        );

        verify(deliveryUseCase, never()).deliverMultiChannel(any());
    }
}
