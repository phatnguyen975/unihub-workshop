package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.inbound.dto.StaffRevokedEventDto;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaffRevokedNotificationHandler
    implements NotificationEventHandler<StaffRevokedEventDto>
{

    private final MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    @Override
    public String supportedEventType() {
        return "StaffRevokedFromWorkshopEvent";
    }

    @Override
    public Class<StaffRevokedEventDto> eventDtoClass() {
        return StaffRevokedEventDto.class;
    }

    @Override
    public void handle(StaffRevokedEventDto event) {
        if (event == null || event.staffUserId() == null) {
            log.warn(
                "Skipping invalid StaffRevokedEventDto: missing staffUserId"
            );
            return;
        }

        String eventKey = "STAFF_REVOKED_FROM_WORKSHOP";
        String actionUrl = "/workshops";

        Map<String, Object> inAppPayload = new HashMap<>();
        if (event.workshopId() != null) {
            inAppPayload.put("workshopId", event.workshopId().toString());
        }
        if (event.workshopTitle() != null) {
            inAppPayload.put("workshopTitle", event.workshopTitle());
        }
        inAppPayload.put("actionUrl", actionUrl);

        String templateName = "staff-revoked";
        String subject =
            "UniHub Workshop — Staff Assignment Revoked: " +
            event.workshopTitle();

        Map<String, Object> emailModel = new HashMap<>();
        if (event.workshopTitle() != null) {
            emailModel.put("workshopTitle", event.workshopTitle());
        }
        if (event.workshopId() != null) {
            emailModel.put("workshopId", event.workshopId().toString());
        }

        boolean hasEmail =
            event.staffEmail() != null && !event.staffEmail().isBlank();

        MultiChannelDeliveryCommand command = new MultiChannelDeliveryCommand(
            event.staffUserId(),
            event.staffEmail(),
            event.idempotencyKey(),
            true, // sendInApp
            eventKey,
            actionUrl,
            inAppPayload,
            true, // sendRealtime
            "notification.new",
            hasEmail, // sendEmail
            subject,
            templateName,
            emailModel
        );

        deliveryUseCase.deliverMultiChannel(command);
    }
}
