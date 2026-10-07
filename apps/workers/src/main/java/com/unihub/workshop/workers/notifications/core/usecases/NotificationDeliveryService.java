package com.unihub.workshop.workers.notifications.core.usecases;

import com.unihub.workshop.workers.notifications.core.models.DeliveryChannel;
import com.unihub.workshop.workers.notifications.core.models.EmailMessage;
import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.DirectEmailDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailRendererPort;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailSenderPort;
import com.unihub.workshop.workers.notifications.core.ports.out.InAppNotificationPort;
import com.unihub.workshop.workers.notifications.core.ports.out.NotificationDeliveryLogPort;
import com.unihub.workshop.workers.notifications.core.ports.out.RealtimeEventPublisherPort;
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
        MultiChannelNotificationDeliveryUseCase
{

    private final EmailSenderPort emailSenderPort;
    private final EmailRendererPort emailRendererPort;
    private final NotificationDeliveryLogPort notificationDeliveryLogPort;
    private final InAppNotificationPort inAppNotificationPort;
    private final RealtimeEventPublisherPort realtimeEventPublisherPort;

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
                command.userId(),
                command.recipientEmail(),
                command.emailSubject(),
                command.emailTemplateName(),
                command.emailTemplateModel(),
                command.idempotencyKey()
            );
        }
    }

    @Override
    public void deliverEmail(
        UUID userId,
        String recipientAddress,
        String subject,
        String templateName,
        Map<String, Object> templateModel,
        String idempotencyKey
    ) {
        if (
            notificationDeliveryLogPort.isAlreadySent(
                recipientAddress,
                DeliveryChannel.EMAIL,
                idempotencyKey
            )
        ) {
            log.info(
                "Skipping duplicate email delivery for recipient='{}', channel=EMAIL, idempotencyKey='{}'",
                recipientAddress,
                idempotencyKey
            );
            return;
        }

        UUID logId = notificationDeliveryLogPort.createPendingLog(
            userId,
            DeliveryChannel.EMAIL,
            recipientAddress,
            idempotencyKey
        );

        try {
            String htmlBody = emailRendererPort.render(
                templateName,
                templateModel
            );
            emailSenderPort.send(
                new EmailMessage(recipientAddress, subject, htmlBody)
            );
            notificationDeliveryLogPort.markSent(logId, idempotencyKey);
            log.info(
                "Successfully delivered email to recipient='{}', logId='{}', idempotencyKey='{}'",
                recipientAddress,
                logId,
                idempotencyKey
            );
        } catch (Exception e) {
            log.error(
                "Failed to deliver email to recipient='{}', logId='{}', idempotencyKey='{}': {}",
                recipientAddress,
                logId,
                idempotencyKey,
                e.getMessage(),
                e
            );
            notificationDeliveryLogPort.markFailed(logId, e.getMessage());
        }
    }
}
