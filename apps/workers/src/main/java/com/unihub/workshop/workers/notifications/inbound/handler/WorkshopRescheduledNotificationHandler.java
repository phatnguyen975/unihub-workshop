package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.WorkshopNotificationCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.core.ports.in.WorkshopNotificationFanOutUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.WorkshopRescheduledEventDto;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopRescheduledNotificationHandler
    implements NotificationEventHandler<WorkshopRescheduledEventDto>
{

    private final WorkshopNotificationFanOutUseCase fanOutUseCase;

    @Override
    public String supportedEventType() {
        return "WorkshopRescheduledEvent";
    }

    @Override
    public Class<WorkshopRescheduledEventDto> eventDtoClass() {
        return WorkshopRescheduledEventDto.class;
    }

    @Override
    public void handle(WorkshopRescheduledEventDto event) {
        if (event == null || event.workshopId() == null) {
            log.warn(
                "Skipping invalid WorkshopRescheduledEventDto: missing workshopId"
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
        if (event.location() != null) {
            inAppPayload.put("location", event.location());
        }
        if (event.previousStartsAt() != null) {
            inAppPayload.put(
                "previousStartsAt",
                event.previousStartsAt().toString()
            );
        }
        if (event.previousEndsAt() != null) {
            inAppPayload.put(
                "previousEndsAt",
                event.previousEndsAt().toString()
            );
        }
        if (event.newStartsAt() != null) {
            inAppPayload.put("newStartsAt", event.newStartsAt().toString());
        }
        if (event.newEndsAt() != null) {
            inAppPayload.put("newEndsAt", event.newEndsAt().toString());
        }

        Map<String, Object> emailModel = new HashMap<>();
        emailModel.put(
            "workshopTitle",
            event.title() != null ? event.title() : "Workshop"
        );
        emailModel.put("location", event.location());
        emailModel.put(
            "previousStartsAt",
            event.previousStartsAt() != null
                ? event.previousStartsAt().toString()
                : null
        );
        emailModel.put(
            "previousEndsAt",
            event.previousEndsAt() != null
                ? event.previousEndsAt().toString()
                : null
        );
        emailModel.put(
            "newStartsAt",
            event.newStartsAt() != null ? event.newStartsAt().toString() : null
        );
        emailModel.put(
            "newEndsAt",
            event.newEndsAt() != null ? event.newEndsAt().toString() : null
        );
        emailModel.put("actionUrl", actionUrl);

        WorkshopNotificationCommand command = new WorkshopNotificationCommand(
            event.workshopId(),
            "WORKSHOP_RESCHEDULED:" +
                event.workshopId() +
                ":" +
                (event.newStartsAt() != null ? event.newStartsAt() : ""),
            slug,
            "WORKSHOP_RESCHEDULED",
            actionUrl,
            inAppPayload,
            "Workshop Rescheduled: " +
                (event.title() != null ? event.title() : "Workshop"),
            "email/workshop-rescheduled",
            emailModel
        );

        fanOutUseCase.fanOutWorkshopNotification(command);
    }
}
