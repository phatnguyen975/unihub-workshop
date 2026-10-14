package com.unihub.workshop.workers.notifications.core.usecases;

import com.unihub.workshop.workers.notifications.core.models.DeliveryChannel;
import com.unihub.workshop.workers.notifications.core.models.DirectEmailDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.models.EmailMessage;
import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.models.OrganizationNotificationCommand;
import com.unihub.workshop.workers.notifications.core.models.OrganizerRecipient;
import com.unihub.workshop.workers.notifications.core.models.WorkshopAttendeeRecipient;
import com.unihub.workshop.workers.notifications.core.models.WorkshopNotificationCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.DirectEmailDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.OrganizationNotificationFanOutUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.WorkshopNotificationFanOutUseCase;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailRendererPort;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailSenderPort;
import com.unihub.workshop.workers.notifications.core.ports.out.InAppNotificationPort;
import com.unihub.workshop.workers.notifications.core.ports.out.NotificationDeliveryLogPort;
import com.unihub.workshop.workers.notifications.core.ports.out.OrganizationRecipientResolverPort;
import com.unihub.workshop.workers.notifications.core.ports.out.RealtimeEventPublisherPort;
import com.unihub.workshop.workers.notifications.core.ports.out.WorkshopAttendeeResolverPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService
    implements
        DirectEmailDeliveryUseCase,
        MultiChannelNotificationDeliveryUseCase,
        OrganizationNotificationFanOutUseCase,
        WorkshopNotificationFanOutUseCase
{

    private final EmailSenderPort emailSenderPort;
    private final EmailRendererPort emailRendererPort;
    private final NotificationDeliveryLogPort notificationDeliveryLogPort;
    private final InAppNotificationPort inAppNotificationPort;
    private final RealtimeEventPublisherPort realtimeEventPublisherPort;
    private final OrganizationRecipientResolverPort organizationRecipientResolverPort;
    private final WorkshopAttendeeResolverPort workshopAttendeeResolverPort;

    @Override
    public void deliverMultiChannel(MultiChannelDeliveryCommand command) {
        if (command == null) {
            log.warn("Skipping null MultiChannelDeliveryCommand");
            return;
        }

        // 1. In-App Notification Channel (Idempotent)
        if (command.sendInApp() && command.userId() != null) {
            try {
                if (
                    inAppNotificationPort.isAlreadyCreated(
                        command.userId(),
                        command.idempotencyKey()
                    )
                ) {
                    log.info(
                        "Skipping duplicate in-app notification for userId='{}', idempotencyKey='{}'",
                        command.userId(),
                        command.idempotencyKey()
                    );
                } else {
                    inAppNotificationPort.createNotification(
                        command.userId(),
                        command.inAppEventKey(),
                        command.inAppActionUrl(),
                        command.inAppPayload(),
                        command.idempotencyKey()
                    );
                }
            } catch (Exception e) {
                log.error(
                    "Error delivering in-app notification for userId='{}', idempotencyKey='{}': {}",
                    command.userId(),
                    command.idempotencyKey(),
                    e.getMessage(),
                    e
                );
            }
        }

        // 2. Real-Time SSE Channel (Soft failure isolated)
        if (command.sendRealtime() && command.userId() != null) {
            try {
                realtimeEventPublisherPort.publishNotificationSignal(
                    command.userId(),
                    command.realtimeEventType() != null
                        ? command.realtimeEventType()
                        : "notification.new"
                );
            } catch (Exception e) {
                log.warn(
                    "Error publishing real-time signal for userId='{}': {}",
                    command.userId(),
                    e.getMessage()
                );
            }
        }

        // 3. Email Channel (Soft failure isolated via deliverEmail)
        if (command.sendEmail() && command.recipientEmail() != null) {
            deliverEmail(
                DirectEmailDeliveryCommand.builder()
                    .userId(command.userId())
                    .recipientAddress(command.recipientEmail())
                    .subject(command.emailSubject())
                    .templateName(command.emailTemplateName())
                    .templateModel(command.emailTemplateModel())
                    .inlineAttachments(command.inlineAttachments())
                    .idempotencyKey(command.idempotencyKey())
                    .build()
            );
        }
    }

    @Override
    public void deliverEmail(DirectEmailDeliveryCommand command) {
        if (
            command == null ||
            command.recipientAddress() == null ||
            command.recipientAddress().isBlank()
        ) {
            log.warn(
                "Skipping invalid DirectEmailDeliveryCommand: missing recipientAddress"
            );
            return;
        }

        if (
            notificationDeliveryLogPort.isAlreadySent(
                command.recipientAddress(),
                DeliveryChannel.EMAIL,
                command.idempotencyKey()
            )
        ) {
            log.info(
                "Skipping duplicate email delivery for recipient='{}', channel=EMAIL, idempotencyKey='{}'",
                command.recipientAddress(),
                command.idempotencyKey()
            );
            return;
        }

        UUID logId = notificationDeliveryLogPort.createPendingLog(
            command.userId(),
            DeliveryChannel.EMAIL,
            command.recipientAddress(),
            command.idempotencyKey()
        );

        try {
            String htmlBody = emailRendererPort.render(
                command.templateName(),
                command.templateModel()
            );
            emailSenderPort.send(
                EmailMessage.builder()
                    .recipientAddress(command.recipientAddress())
                    .subject(command.subject())
                    .htmlBody(htmlBody)
                    .inlineAttachments(command.inlineAttachments())
                    .build()
            );
            notificationDeliveryLogPort.markSent(
                logId,
                command.idempotencyKey()
            );
            log.info(
                "Successfully delivered email to recipient='{}', logId='{}', idempotencyKey='{}'",
                command.recipientAddress(),
                logId,
                command.idempotencyKey()
            );
        } catch (Exception e) {
            log.error(
                "Failed to deliver email to recipient='{}', logId='{}', idempotencyKey='{}': {}",
                command.recipientAddress(),
                logId,
                command.idempotencyKey(),
                e.getMessage(),
                e
            );
            notificationDeliveryLogPort.markFailed(logId, e.getMessage());
        }
    }

    @Override
    public void fanOutOrganizationNotification(
        OrganizationNotificationCommand command
    ) {
        if (command == null || command.organizationId() == null) {
            log.warn(
                "Skipping invalid OrganizationNotificationCommand: missing organizationId"
            );
            return;
        }

        List<OrganizerRecipient> organizers =
            organizationRecipientResolverPort.findActiveOrganizers(
                command.organizationId()
            );

        if (organizers.isEmpty()) {
            log.warn(
                "No active organizers found for organizationId='{}'. Skipping notification fan-out.",
                command.organizationId()
            );
            return;
        }

        log.info(
            "Fanning out organization notification for organizationId='{}' to {} active organizers",
            command.organizationId(),
            organizers.size()
        );

        for (OrganizerRecipient organizer : organizers) {
            String idempotencyKey =
                command.idempotencyPrefix() +
                ":" +
                command.organizationId() +
                ":" +
                command.sourceFileKey() +
                ":" +
                organizer.id();

            Map<String, Object> inAppPayload = new HashMap<>();
            inAppPayload.put(
                "organizationId",
                command.organizationId().toString()
            );
            inAppPayload.put("sourceFileKey", command.sourceFileKey());
            inAppPayload.put("actionUrl", command.inAppActionUrl());

            Map<String, Object> emailModel = new HashMap<>();
            emailModel.put(
                "recipientName",
                organizer.fullName() != null
                    ? organizer.fullName()
                    : "Organizer"
            );
            emailModel.put("sourceFileKey", command.sourceFileKey());
            emailModel.put("actionUrl", command.inAppActionUrl());

            MultiChannelDeliveryCommand deliveryCmd =
                MultiChannelDeliveryCommand.builder()
                    .userId(organizer.id())
                    .recipientEmail(organizer.email())
                    .idempotencyKey(idempotencyKey)
                    .sendInApp(true)
                    .inAppEventKey(command.inAppEventKey())
                    .inAppActionUrl(command.inAppActionUrl())
                    .inAppPayload(inAppPayload)
                    .sendRealtime(true)
                    .realtimeEventType("notification.new")
                    .sendEmail(true)
                    .emailSubject(command.emailSubject())
                    .emailTemplateName(command.emailTemplateName())
                    .emailTemplateModel(emailModel)
                    .build();

            deliverMultiChannel(deliveryCmd);
        }
    }

    @Override
    public void fanOutWorkshopNotification(
        WorkshopNotificationCommand command
    ) {
        if (command == null || command.workshopId() == null) {
            log.warn(
                "Skipping invalid WorkshopNotificationCommand: missing workshopId"
            );
            return;
        }

        int page = 0;
        int pageSize = 500;
        long totalDispatched = 0;

        while (true) {
            List<WorkshopAttendeeRecipient> attendees =
                workshopAttendeeResolverPort.findConfirmedAttendees(
                    command.workshopId(),
                    page,
                    pageSize
                );

            if (attendees.isEmpty()) {
                if (page == 0) {
                    log.warn(
                        "No confirmed attendees found for workshopId='{}'. Skipping notification fan-out.",
                        command.workshopId()
                    );
                }
                break;
            }

            for (WorkshopAttendeeRecipient attendee : attendees) {
                String idempotencyKey =
                    command.idempotencyPrefix() + ":" + attendee.id();

                Map<String, Object> emailModel = new HashMap<>(
                    command.emailTemplateModel()
                );
                emailModel.put(
                    "recipientName",
                    attendee.fullName() != null
                        ? attendee.fullName()
                        : "Student"
                );

                MultiChannelDeliveryCommand deliveryCmd =
                    MultiChannelDeliveryCommand.builder()
                        .userId(attendee.id())
                        .recipientEmail(attendee.email())
                        .idempotencyKey(idempotencyKey)
                        .sendInApp(true)
                        .inAppEventKey(command.inAppEventKey())
                        .inAppActionUrl(command.inAppActionUrl())
                        .inAppPayload(command.inAppPayload())
                        .sendRealtime(true)
                        .realtimeEventType("notification.new")
                        .sendEmail(true)
                        .emailSubject(command.emailSubject())
                        .emailTemplateName(command.emailTemplateName())
                        .emailTemplateModel(emailModel)
                        .build();

                deliverMultiChannel(deliveryCmd);
                totalDispatched++;
            }

            if (attendees.size() < pageSize) {
                break;
            }
            page++;
        }

        log.info(
            "Completed workshop notification fan-out for workshopId='{}', total recipients={}",
            command.workshopId(),
            totalDispatched
        );
    }
}
