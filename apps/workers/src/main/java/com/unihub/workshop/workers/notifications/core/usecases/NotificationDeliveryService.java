package com.unihub.workshop.workers.notifications.core.usecases;

import com.unihub.workshop.workers.notifications.core.models.DeliveryChannel;
import com.unihub.workshop.workers.notifications.core.models.EmailMessage;
import com.unihub.workshop.workers.notifications.core.ports.in.DirectEmailDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailRendererPort;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailSenderPort;
import com.unihub.workshop.workers.notifications.core.ports.out.NotificationDeliveryLogPort;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService implements DirectEmailDeliveryUseCase {

    private final EmailSenderPort emailSenderPort;
    private final EmailRendererPort emailRendererPort;
    private final NotificationDeliveryLogPort notificationDeliveryLogPort;

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
