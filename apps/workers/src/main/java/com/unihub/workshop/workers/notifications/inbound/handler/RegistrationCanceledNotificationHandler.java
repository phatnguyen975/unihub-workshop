package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.inbound.dto.RegistrationCanceledEventDto;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationCanceledNotificationHandler
    implements NotificationEventHandler<RegistrationCanceledEventDto>
{

    private final MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    @Override
    public String supportedEventType() {
        return "RegistrationCanceledEvent";
    }

    @Override
    public Class<RegistrationCanceledEventDto> eventDtoClass() {
        return RegistrationCanceledEventDto.class;
    }

    @Override
    public void handle(RegistrationCanceledEventDto event) {
        if (
            event == null ||
            event.registrationId() == null ||
            event.studentId() == null
        ) {
            log.warn(
                "Skipping invalid RegistrationCanceledEventDto: missing registrationId or studentId"
            );
            return;
        }

        String slug =
            event.workshopSlug() != null
                ? event.workshopSlug()
                : event.workshopId() != null
                  ? event.workshopId().toString()
                  : "";
        String actionUrl = slug.isEmpty() ? "/workshops" : "/workshops/" + slug;
        String eventKey = "REGISTRATION_CANCELED";

        // 1. In-App Notification Payload
        Map<String, Object> inAppPayload = new HashMap<>();
        inAppPayload.put("registrationId", event.registrationId().toString());
        if (event.workshopId() != null) {
            inAppPayload.put("workshopId", event.workshopId().toString());
        }
        if (event.workshopTitle() != null) {
            inAppPayload.put("workshopTitle", event.workshopTitle());
        }
        if (event.startsAt() != null) {
            inAppPayload.put("startsAt", event.startsAt().toString());
        }
        if (event.endsAt() != null) {
            inAppPayload.put("endsAt", event.endsAt().toString());
        }
        if (event.location() != null) {
            inAppPayload.put("location", event.location());
        }
        if (event.canceledByRole() != null) {
            inAppPayload.put("canceledByRole", event.canceledByRole());
        }
        if (event.reason() != null && !event.reason().isBlank()) {
            inAppPayload.put("reason", event.reason());
        }
        if (event.refundAmount() != null) {
            inAppPayload.put("refundAmount", event.refundAmount());
        }
        if (event.currency() != null) {
            inAppPayload.put("currency", event.currency());
        }
        inAppPayload.put("actionUrl", actionUrl);

        // 2. Email Template Model
        String subject =
            "UniHub Workshop — Registration Canceled: " +
            (event.workshopTitle() != null
                ? event.workshopTitle()
                : "Workshop");
        String templateName = "registration-canceled";

        Map<String, Object> emailModel = new HashMap<>();
        emailModel.put(
            "studentName",
            event.studentName() != null ? event.studentName() : "Student"
        );
        emailModel.put(
            "workshopTitle",
            event.workshopTitle() != null ? event.workshopTitle() : "Workshop"
        );
        if (event.startsAt() != null) {
            emailModel.put("startsAt", event.startsAt().toString());
        }
        if (event.endsAt() != null) {
            emailModel.put("endsAt", event.endsAt().toString());
        }
        if (event.location() != null) {
            emailModel.put("location", event.location());
        }
        if (event.canceledByRole() != null) {
            emailModel.put("canceledByRole", event.canceledByRole());
        }
        if (event.reason() != null && !event.reason().isBlank()) {
            emailModel.put("reason", event.reason());
        }
        if (event.refundAmount() != null) {
            emailModel.put("refundAmount", event.refundAmount());
        }
        if (event.currency() != null) {
            emailModel.put("currency", event.currency());
        }
        emailModel.put("actionUrl", actionUrl);

        boolean hasEmail =
            event.studentEmail() != null && !event.studentEmail().isBlank();

        MultiChannelDeliveryCommand command =
            MultiChannelDeliveryCommand.builder()
                .userId(event.studentId())
                .recipientEmail(event.studentEmail())
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
}
