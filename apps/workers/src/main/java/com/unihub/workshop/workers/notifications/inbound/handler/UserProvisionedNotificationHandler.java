package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.inbound.dto.UserProvisionedEventDto;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserProvisionedNotificationHandler
    implements NotificationEventHandler<UserProvisionedEventDto>
{

    private final MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    @Override
    public String supportedEventType() {
        return "UserProvisionedEvent";
    }

    @Override
    public Class<UserProvisionedEventDto> eventDtoClass() {
        return UserProvisionedEventDto.class;
    }

    @Override
    public void handle(UserProvisionedEventDto event) {
        if (event == null || event.userId() == null) {
            log.warn(
                "Skipping invalid UserProvisionedEventDto: missing userId"
            );
            return;
        }

        String eventKey = "USER_PROVISIONED";

        // In-app payload must NEVER contain temporaryPassword
        Map<String, Object> inAppPayload = new HashMap<>();
        if (event.fullName() != null) {
            inAppPayload.put("fullName", event.fullName());
        }
        if (event.email() != null) {
            inAppPayload.put("email", event.email());
        }
        if (event.role() != null) {
            inAppPayload.put("role", event.role());
        }
        if (event.orgSlug() != null) {
            inAppPayload.put("orgSlug", event.orgSlug());
        }

        String templateName = "user-provisioned";
        String subject = "UniHub Workshop — Account Provisioned";

        // Temporary password is provided ONLY to the email template model
        Map<String, Object> emailModel = new HashMap<>();
        if (event.fullName() != null) {
            emailModel.put("fullName", event.fullName());
        }
        if (event.email() != null) {
            emailModel.put("email", event.email());
        }
        if (event.temporaryPassword() != null) {
            emailModel.put("temporaryPassword", event.temporaryPassword());
        }

        boolean hasEmail = event.email() != null && !event.email().isBlank();

        MultiChannelDeliveryCommand command =
            MultiChannelDeliveryCommand.builder()
                .userId(event.userId())
                .recipientEmail(event.email())
                .idempotencyKey(event.idempotencyKey())
                .sendInApp(true)
                .inAppEventKey(eventKey)
                .inAppActionUrl(null)
                .inAppPayload(inAppPayload)
                .sendRealtime(false)
                .realtimeEventType(null)
                .sendEmail(hasEmail)
                .emailSubject(subject)
                .emailTemplateName(templateName)
                .emailTemplateModel(emailModel)
                .build();

        deliveryUseCase.deliverMultiChannel(command);
    }
}
