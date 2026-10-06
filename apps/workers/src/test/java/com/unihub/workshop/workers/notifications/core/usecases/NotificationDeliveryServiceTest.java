package com.unihub.workshop.workers.notifications.core.usecases;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.notifications.core.models.DeliveryChannel;
import com.unihub.workshop.workers.notifications.core.models.EmailMessage;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailRendererPort;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailSenderPort;
import com.unihub.workshop.workers.notifications.core.ports.out.NotificationDeliveryLogPort;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock
    private EmailSenderPort emailSenderPort;

    @Mock
    private EmailRendererPort emailRendererPort;

    @Mock
    private NotificationDeliveryLogPort notificationDeliveryLogPort;

    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationDeliveryService(
            emailSenderPort,
            emailRendererPort,
            notificationDeliveryLogPort
        );
    }

    @Test
    @DisplayName(
        "Should successfully render, send email, and mark delivery log as SENT"
    )
    void shouldSendEmailAndRecordSentAuditLogWhenValid() {
        UUID userId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        String recipient = "student@hcmus.edu.vn";
        String subject = "Your OTP";
        String templateName = "otp-activation";
        Map<String, Object> model = Map.of("otp", "123456");
        String idempotencyKey = "OTP:" + userId + ":1700000000";

        when(
            notificationDeliveryLogPort.isAlreadySent(
                recipient,
                DeliveryChannel.EMAIL,
                idempotencyKey
            )
        ).thenReturn(false);
        when(
            notificationDeliveryLogPort.createPendingLog(
                userId,
                DeliveryChannel.EMAIL,
                recipient,
                idempotencyKey
            )
        ).thenReturn(logId);
        when(emailRendererPort.render(templateName, model)).thenReturn(
            "<html>123456</html>"
        );

        service.deliverEmail(
            userId,
            recipient,
            subject,
            templateName,
            model,
            idempotencyKey
        );

        verify(emailSenderPort).send(
            new EmailMessage(recipient, subject, "<html>123456</html>")
        );
        verify(notificationDeliveryLogPort).markSent(logId, idempotencyKey);
    }

    @Test
    @DisplayName(
        "Should skip sending email when idempotency check indicates message was already SENT"
    )
    void shouldSkipSendingWhenAlreadySentIdempotent() {
        UUID userId = UUID.randomUUID();
        String recipient = "student@hcmus.edu.vn";
        String idempotencyKey = "OTP:" + userId + ":1700000000";

        when(
            notificationDeliveryLogPort.isAlreadySent(
                recipient,
                DeliveryChannel.EMAIL,
                idempotencyKey
            )
        ).thenReturn(true);

        service.deliverEmail(
            userId,
            recipient,
            "Subject",
            "template",
            Map.of(),
            idempotencyKey
        );

        verify(notificationDeliveryLogPort, never()).createPendingLog(
            any(),
            any(),
            any(),
            any()
        );
        verify(emailRendererPort, never()).render(any(), any());
        verify(emailSenderPort, never()).send(any());
    }

    @Test
    @DisplayName(
        "Should mark delivery log as FAILED when email sender throws exception without rethrowing"
    )
    void shouldMarkFailedAuditLogWhenEmailSenderThrowsException() {
        UUID userId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        String recipient = "student@hcmus.edu.vn";
        String idempotencyKey = "OTP:" + userId + ":1700000000";

        when(
            notificationDeliveryLogPort.isAlreadySent(
                recipient,
                DeliveryChannel.EMAIL,
                idempotencyKey
            )
        ).thenReturn(false);
        when(
            notificationDeliveryLogPort.createPendingLog(
                userId,
                DeliveryChannel.EMAIL,
                recipient,
                idempotencyKey
            )
        ).thenReturn(logId);
        when(emailRendererPort.render(any(), any())).thenReturn(
            "<html>Body</html>"
        );
        org.mockito.Mockito.doThrow(
            new RuntimeException("SMTP connection timeout")
        )
            .when(emailSenderPort)
            .send(any());

        assertDoesNotThrow(() ->
            service.deliverEmail(
                userId,
                recipient,
                "Subject",
                "template",
                Map.of(),
                idempotencyKey
            )
        );

        verify(notificationDeliveryLogPort).markFailed(
            eq(logId),
            eq("SMTP connection timeout")
        );
        verify(notificationDeliveryLogPort, never()).markSent(any(), any());
    }
}
