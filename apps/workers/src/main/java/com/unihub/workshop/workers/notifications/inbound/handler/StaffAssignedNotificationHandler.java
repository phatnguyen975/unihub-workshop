package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.inbound.dto.StaffAssignedEventDto;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
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

    private static final DateTimeFormatter SCHEDULE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (O)", Locale.ENGLISH);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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
        if (event.endsAt() != null) {
            inAppPayload.put("endsAt", event.endsAt().toString());
        }
        if (event.location() != null) {
            inAppPayload.put("location", event.location());
        }

        String templateName = "staff-assigned";
        String subject =
            "UniHub Workshop — Staff Assignment: " + event.workshopTitle();

        Map<String, Object> emailModel = new HashMap<>();
        if (event.workshopTitle() != null) {
            emailModel.put("workshopTitle", event.workshopTitle());
        }
        if (event.workshopId() != null) {
            emailModel.put("workshopId", event.workshopId().toString());
        }
        if (event.startsAt() != null) {
            emailModel.put("startsAt", formatSchedule(event.startsAt()));
        }
        if (event.endsAt() != null) {
            emailModel.put("endsAt", formatSchedule(event.endsAt()));
        }
        if (event.location() != null) {
            emailModel.put("location", event.location());
        }

        boolean hasEmail =
            event.staffEmail() != null && !event.staffEmail().isBlank();

        MultiChannelDeliveryCommand command =
            MultiChannelDeliveryCommand.builder()
                .userId(event.staffUserId())
                .recipientEmail(event.staffEmail())
                .idempotencyKey(event.idempotencyKey())
                .sendInApp(true)
                .inAppEventKey(eventKey)
                .inAppActionUrl(actionUrl)
                .inAppPayload(inAppPayload)
                .sendRealtime(true)
                .realtimeEventType("notification.new")
                .sendEmail(hasEmail)
                .emailSubject(subject)
                .emailTemplateName(templateName)
                .emailTemplateModel(emailModel)
                .build();

        deliveryUseCase.deliverMultiChannel(command);
    }

    private String formatSchedule(Instant instant) {
        if (instant == null) {
            return null;
        }
        return SCHEDULE_FORMATTER.format(instant.atZone(DEFAULT_ZONE));
    }
}
