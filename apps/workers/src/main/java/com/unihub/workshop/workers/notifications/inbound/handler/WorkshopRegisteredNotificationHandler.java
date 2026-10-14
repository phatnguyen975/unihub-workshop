package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.core.ports.out.QrCodeGeneratorPort;
import com.unihub.workshop.workers.notifications.inbound.dto.WorkshopRegisteredEventDto;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopRegisteredNotificationHandler
    implements NotificationEventHandler<WorkshopRegisteredEventDto>
{

    private final MultiChannelNotificationDeliveryUseCase deliveryUseCase;
    private final QrCodeGeneratorPort qrCodeGeneratorPort;

    @Override
    public String supportedEventType() {
        return "WorkshopRegisteredEvent";
    }

    @Override
    public Class<WorkshopRegisteredEventDto> eventDtoClass() {
        return WorkshopRegisteredEventDto.class;
    }

    @Override
    public void handle(WorkshopRegisteredEventDto event) {
        if (
            event == null ||
            event.registrationId() == null ||
            event.studentId() == null
        ) {
            log.warn(
                "Skipping invalid WorkshopRegisteredEventDto: missing registrationId or studentId"
            );
            return;
        }

        String slug =
            event.workshopSlug() != null
                ? event.workshopSlug()
                : event.workshopId().toString();
        String actionUrl = "/workshops/" + slug;
        String eventKey = "WORKSHOP_REGISTERED";

        // 1. In-App Notification Payload
        Map<String, Object> inAppPayload = new HashMap<>();
        inAppPayload.put("registrationId", event.registrationId().toString());
        inAppPayload.put("workshopId", event.workshopId().toString());
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
        inAppPayload.put("actionUrl", actionUrl);

        // 2. Inline Attachments (QR Ticket Image)
        Map<String, byte[]> inlineAttachments = new HashMap<>();
        if (event.qrPayload() != null && !event.qrPayload().isBlank()) {
            String qrBarcodeContent;
            String trimmedPayload = event.qrPayload().trim();
            if (trimmedPayload.startsWith("{")) {
                qrBarcodeContent = String.format(
                    "{\"payload\":%s,\"signature\":\"%s\"}",
                    trimmedPayload,
                    event.qrSignature() != null ? event.qrSignature() : ""
                );
            } else {
                qrBarcodeContent = String.format(
                    "{\"payload\":\"%s\",\"signature\":\"%s\"}",
                    trimmedPayload,
                    event.qrSignature() != null ? event.qrSignature() : ""
                );
            }

            byte[] qrPng = qrCodeGeneratorPort.generateQrPng(
                qrBarcodeContent,
                300,
                300
            );
            inlineAttachments.put("ticket-qr", qrPng);
        }

        // 3. Email Template Model
        String subject =
            "UniHub Workshop — Registration Confirmation: " +
            (event.workshopTitle() != null
                ? event.workshopTitle()
                : "Workshop");
        String templateName = "workshop-registration-confirmation";

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
                .inlineAttachments(inlineAttachments)
                .build();

        deliveryUseCase.deliverMultiChannel(command);
    }
}
