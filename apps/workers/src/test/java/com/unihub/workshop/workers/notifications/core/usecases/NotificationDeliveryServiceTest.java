package com.unihub.workshop.workers.notifications.core.usecases;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.notifications.core.models.DeliveryChannel;
import com.unihub.workshop.workers.notifications.core.models.EmailMessage;
import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailRendererPort;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailSenderPort;
import com.unihub.workshop.workers.notifications.core.ports.out.InAppNotificationPort;
import com.unihub.workshop.workers.notifications.core.ports.out.NotificationDeliveryLogPort;
import com.unihub.workshop.workers.notifications.core.ports.out.RealtimeEventPublisherPort;
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

    @Mock
    private InAppNotificationPort inAppNotificationPort;

    @Mock
    private RealtimeEventPublisherPort realtimeEventPublisherPort;

    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationDeliveryService(
            emailSenderPort,
            emailRendererPort,
            notificationDeliveryLogPort,
            inAppNotificationPort,
            realtimeEventPublisherPort
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

    @Test
    @DisplayName(
        "Should deliver across In-App, Real-Time SSE, and Email channels when all enabled"
    )
    void shouldDeliverAcrossAllChannelsWhenEnabled() {
        UUID userId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        String recipient = "staff@hcmus.edu.vn";
        String idempotencyKey = "STAFF_ASSIGNED:" + userId + ":W-123";

        when(
            inAppNotificationPort.isAlreadyCreated(userId, idempotencyKey)
        ).thenReturn(false);
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
        when(emailRendererPort.render(eq("staff-assigned"), any())).thenReturn(
            "<html>Assigned</html>"
        );

        MultiChannelDeliveryCommand command = new MultiChannelDeliveryCommand(
            userId,
            recipient,
            idempotencyKey,
            true,
            "STAFF_ASSIGNED_TO_WORKSHOP",
            "/workshops/W-123",
            Map.of("workshopTitle", "Spring Cloud"),
            true,
            "notification.new",
            true,
            "Staff Assigned",
            "staff-assigned",
            Map.of("workshopTitle", "Spring Cloud")
        );

        service.deliverMultiChannel(command);

        verify(inAppNotificationPort).createNotification(
            eq(userId),
            eq("STAFF_ASSIGNED_TO_WORKSHOP"),
            eq("/workshops/W-123"),
            eq(Map.of("workshopTitle", "Spring Cloud")),
            eq(idempotencyKey)
        );
        verify(realtimeEventPublisherPort).publishNotificationSignal(
            eq(userId),
            eq("notification.new")
        );
        verify(emailSenderPort).send(any());
        verify(notificationDeliveryLogPort).markSent(
            eq(logId),
            eq(idempotencyKey)
        );
    }

    @Test
    @DisplayName(
        "Should skip In-App persistence when already created with same idempotency key"
    )
    void shouldSkipInAppWhenAlreadyCreated() {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "USER_PROVISIONED:" + userId;

        when(
            inAppNotificationPort.isAlreadyCreated(userId, idempotencyKey)
        ).thenReturn(true);

        MultiChannelDeliveryCommand command = new MultiChannelDeliveryCommand(
            userId,
            "organizer@hcmus.edu.vn",
            idempotencyKey,
            true,
            "USER_PROVISIONED",
            null,
            Map.of("role", "ORGANIZER"),
            false,
            null,
            false,
            null,
            null,
            Map.of()
        );

        service.deliverMultiChannel(command);

        verify(inAppNotificationPort, never()).createNotification(
            any(),
            any(),
            any(),
            any(),
            any()
        );
        verify(realtimeEventPublisherPort, never()).publishNotificationSignal(
            any(),
            any()
        );
        verify(emailSenderPort, never()).send(any());
    }

    @Test
    @DisplayName(
        "Should isolate Real-Time SSE failure as soft failure without aborting In-App and Email"
    )
    void shouldIsolateRealtimeFailureWithoutAbortingOtherChannels() {
        UUID userId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        String recipient = "staff@hcmus.edu.vn";
        String idempotencyKey = "STAFF_REVOKED:" + userId + ":W-123";

        when(
            inAppNotificationPort.isAlreadyCreated(userId, idempotencyKey)
        ).thenReturn(false);
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
        when(emailRendererPort.render(eq("staff-revoked"), any())).thenReturn(
            "<html>Revoked</html>"
        );
        doThrow(new RuntimeException("Redis connection refused"))
            .when(realtimeEventPublisherPort)
            .publishNotificationSignal(any(), any());

        MultiChannelDeliveryCommand command = new MultiChannelDeliveryCommand(
            userId,
            recipient,
            idempotencyKey,
            true,
            "STAFF_REVOKED_FROM_WORKSHOP",
            "/workshops",
            Map.of("workshopTitle", "Spring Cloud"),
            true,
            "notification.new",
            true,
            "Staff Revoked",
            "staff-revoked",
            Map.of("workshopTitle", "Spring Cloud")
        );

        assertDoesNotThrow(() -> service.deliverMultiChannel(command));

        verify(inAppNotificationPort).createNotification(
            any(),
            any(),
            any(),
            any(),
            any()
        );
        verify(emailSenderPort).send(any());
        verify(notificationDeliveryLogPort).markSent(
            eq(logId),
            eq(idempotencyKey)
        );
    }
}
