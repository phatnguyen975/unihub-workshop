package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.inbound.dto.StaffAssignedEventDto;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaffAssignedNotificationHandler
    implements NotificationEventHandler<StaffAssignedEventDto>
{

    private final MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    @Override
    public String supportedEventType() {
        return "StaffAssignedToWorkshopEvent";
    }

    @Override
    public Class<StaffAssignedEventDto> eventDtoClass() {
        return StaffAssignedEventDto.class;
    }

    @Override
    public void handle(StaffAssignedEventDto event) {
        if (event == null || event.staffUserId() == null) {
            log.warn(
                "Skipping invalid StaffAssignedEventDto: missing staffUserId"
            );
            return;
        }

        String eventKey = "STAFF_ASSIGNED_TO_WORKSHOP";
        String actionUrl = "/workshops/" + event.workshopId();

        Map<String, Object> inAppPayload = new HashMap<>();
        if (event.workshopId() != null) {
            inAppPayload.put("workshopId", event.workshopId().toString());
        }
        if (event.workshopTitle() != null) {
            inAppPayload.put("workshopTitle", event.workshopTitle());
        }
        inAppPayload.put("actionUrl", actionUrl);
        if (event.startsAt() != null) {
            inAppPayload.put("startsAt", event.startsAt().toString());
        }
        if (event.location() != null) {
            inAppPayload.put("location", event.location());
        }

        String templateName = "staff-assigned";
        String subject =
            "UniHub Workshop - Staff Assignment: " + event.workshopTitle();

        Map<String, Object> emailModel = new HashMap<>();
        if (event.workshopTitle() != null) {
            emailModel.put("workshopTitle", event.workshopTitle());
        }
        if (event.workshopId() != null) {
            emailModel.put("workshopId", event.workshopId().toString());
        }
        if (event.startsAt() != null) {
            emailModel.put("startsAt", event.startsAt().toString());
        }
        if (event.location() != null) {
            emailModel.put("location", event.location());
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
