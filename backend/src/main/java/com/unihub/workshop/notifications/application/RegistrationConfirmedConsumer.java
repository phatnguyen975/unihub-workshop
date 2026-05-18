package com.unihub.workshop.notifications.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.repository.UserRepository;
import com.unihub.workshop.notifications.domain.model.NotificationChannel;
import com.unihub.workshop.notifications.domain.model.NotificationLog;
import com.unihub.workshop.notifications.domain.model.NotificationStatus;
import com.unihub.workshop.notifications.domain.repository.NotificationLogRepository;
import com.unihub.workshop.notifications.infrastructure.email.EmailSenderProvider;
import com.unihub.workshop.notifications.infrastructure.inapp.InAppNotificationProvider;
import com.unihub.workshop.registrations.domain.events.RegistrationConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegistrationConfirmedConsumer {

    private static final String EVENT_TYPE = "RegistrationConfirmed";
    private static final String IN_APP_TITLE = "Ticket Confirmed";
    private static final String IN_APP_MESSAGE = "Your registration for workshop is confirmed.";
    private static final String IN_APP_TYPE = "REGISTRATION";

    private final RegistrationQrCodeJwtService qrCodeJwtService;
    private final InAppNotificationProvider inAppNotificationProvider;
    private final EmailSenderProvider emailSenderProvider;
    private final NotificationLogRepository notificationLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(RegistrationConfirmedEvent event) {
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new IllegalStateException("User not found for registration confirmation: " + event.userId()));

        String qrToken = qrCodeJwtService.generate(event.registrationId(), event.userId(), event.workshopId());

        inAppNotificationProvider.sendInAppNotification(
                event.userId(),
                IN_APP_TITLE,
                IN_APP_MESSAGE,
                IN_APP_TYPE,
                event.registrationId());

        NotificationLog logEntry = createQueuedLog(user.getEmail(), event, qrToken);
        notificationLogRepository.save(logEntry);

        try {
            String subject = "Your workshop registration is confirmed";
            String emailBody = buildEmailBody(user, qrToken);
            emailSenderProvider.sendEmail(user.getEmail(), subject, emailBody);

            logEntry.setStatus(NotificationStatus.SENT);
            logEntry.setStatusAt(OffsetDateTime.now());
            notificationLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.error("Failed to send registration confirmation email for registrationId={} userId={}",
                    event.registrationId(), event.userId(), ex);
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setStatusAt(OffsetDateTime.now());
            logEntry.setErrorMessage(ex.getMessage());
            notificationLogRepository.save(logEntry);
        }
    }

    private NotificationLog createQueuedLog(String recipient, RegistrationConfirmedEvent event, String qrToken) {
        NotificationLog logEntry = new NotificationLog();
        logEntry.setRegistrationId(event.registrationId());
        logEntry.setEventType(EVENT_TYPE);
        logEntry.setChannel(NotificationChannel.EMAIL);
        logEntry.setStatus(NotificationStatus.QUEUED);
        logEntry.setRecipientAddress(recipient);
        logEntry.setStatusAt(OffsetDateTime.now());
        logEntry.setPayload(buildPayload(event, qrToken));
        return logEntry;
    }

    private String buildEmailBody(User user, String qrToken) {
        String encodedQrToken = URLEncoder.encode(qrToken, StandardCharsets.UTF_8);
        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=" + encodedQrToken;
        return """
                <html>
                  <body style="font-family:Arial,sans-serif;line-height:1.6;color:#1f2937;">
                    <h2>Ticket Confirmed</h2>
                    <p>Hello %s,</p>
                    <p>Your registration for the workshop is confirmed.</p>
                    <p>Scan the QR code below at check-in:</p>
                    <p><img src="%s" alt="Workshop QR code" style="max-width:240px;height:auto;border:1px solid #e5e7eb;padding:12px;border-radius:12px;" /></p>
                    <p style="word-break:break-all;"><strong>QR token:</strong> %s</p>
                  </body>
                </html>
                """.formatted(user.getFullName(), qrImageUrl, qrToken);
    }

    private String buildPayload(RegistrationConfirmedEvent event, String qrToken) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "registrationId", event.registrationId(),
                    "userId", event.userId(),
                    "workshopId", event.workshopId(),
                    "qrToken", qrToken));
        } catch (Exception ex) {
            log.warn("Failed to serialize registration confirmation payload for registrationId={}", event.registrationId(), ex);
            return "{}";
        }
    }
}
