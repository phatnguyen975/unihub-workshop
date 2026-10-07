package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.WorkshopNotificationCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.core.ports.in.WorkshopNotificationFanOutUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.WorkshopCanceledEventDto;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopCanceledNotificationHandler
    implements NotificationEventHandler<WorkshopCanceledEventDto>
{

    private final WorkshopNotificationFanOutUseCase fanOutUseCase;

    @Override
    public String supportedEventType() {
        return "WorkshopCanceledEvent";
    }

    @Override
    public Class<WorkshopCanceledEventDto> eventDtoClass() {
        return WorkshopCanceledEventDto.class;
    }

    @Override
    public void handle(WorkshopCanceledEventDto event) {
        if (event == null || event.workshopId() == null) {
            log.warn(
                "Skipping invalid WorkshopCanceledEventDto: missing workshopId"
            );
            return;
        }

        String slug =
            event.slug() != null ? event.slug() : event.workshopId().toString();
        String actionUrl = "/workshops/" + slug;

        Map<String, Object> inAppPayload = new HashMap<>();
        inAppPayload.put("workshopId", event.workshopId().toString());
        if (event.title() != null) {
            inAppPayload.put("workshopTitle", event.title());
        }
        inAppPayload.put("slug", slug);
        inAppPayload.put("actionUrl", actionUrl);
        if (event.reason() != null) {
            inAppPayload.put("reason", event.reason());
        }

        Map<String, Object> emailModel = new HashMap<>();
        emailModel.put(
            "workshopTitle",
            event.title() != null ? event.title() : "Workshop"
        );
        emailModel.put("reason", event.reason());
        emailModel.put("actionUrl", actionUrl);

        WorkshopNotificationCommand command = new WorkshopNotificationCommand(
            event.workshopId(),
            "WORKSHOP_CANCELED:" + event.workshopId(),
            slug,
            "WORKSHOP_CANCELED",
            actionUrl,
            inAppPayload,
            "Workshop Canceled: " +
                (event.title() != null ? event.title() : "Workshop"),
            "email/workshop-canceled",
            emailModel
        );

        fanOutUseCase.fanOutWorkshopNotification(command);
    }
}
