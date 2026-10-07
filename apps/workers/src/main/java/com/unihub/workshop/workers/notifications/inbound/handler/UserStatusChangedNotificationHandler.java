package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.inbound.dto.UserStatusChangedEventDto;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusChangedNotificationHandler
    implements NotificationEventHandler<UserStatusChangedEventDto>
{

    private final MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    @Override
    public String supportedEventType() {
        return "UserStatusChangedEvent";
    }

    @Override
    public Class<UserStatusChangedEventDto> eventDtoClass() {
        return UserStatusChangedEventDto.class;
    }

    @Override
    public void handle(UserStatusChangedEventDto event) {
        if (event == null || event.userId() == null) {
            log.warn(
                "Skipping invalid UserStatusChangedEventDto: missing userId"
            );
            return;
        }

        String eventKey = "USER_STATUS_CHANGED";
        String status = Boolean.TRUE.equals(event.isActive())
            ? "ACTIVE"
            : "INACTIVE";

        Map<String, Object> inAppPayload = new HashMap<>();
        if (event.fullName() != null) {
            inAppPayload.put("fullName", event.fullName());
        }
        if (event.email() != null) {
            inAppPayload.put("email", event.email());
        }
        inAppPayload.put("status", status);

        String templateName = "user-status-changed";
        String subject = "UniHub Workshop - Account Status Update";

        Map<String, Object> emailModel = new HashMap<>();
        if (event.fullName() != null) {
            emailModel.put("fullName", event.fullName());
        }
        if (event.email() != null) {
            emailModel.put("email", event.email());
        }
        emailModel.put("status", status);
        emailModel.put("isActive", Boolean.TRUE.equals(event.isActive()));

        boolean hasEmail = event.email() != null && !event.email().isBlank();

        MultiChannelDeliveryCommand command = new MultiChannelDeliveryCommand(
            event.userId(),
            event.email(),
            event.idempotencyKey(),
            true, // sendInApp
            eventKey,
            null, // action_url = null for account-level events
            inAppPayload,
            false, // NO SSE for UserStatusChangedEvent
            null,
            hasEmail, // sendEmail
            subject,
            templateName,
            emailModel
        );

        deliveryUseCase.deliverMultiChannel(command);
    }
}
