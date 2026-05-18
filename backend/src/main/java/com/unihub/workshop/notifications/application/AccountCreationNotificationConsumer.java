package com.unihub.workshop.notifications.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.domain.events.UserAccountCreatedEvent;
import com.unihub.workshop.notifications.domain.model.NotificationChannel;
import com.unihub.workshop.notifications.domain.model.NotificationLog;
import com.unihub.workshop.notifications.domain.model.NotificationStatus;
import com.unihub.workshop.notifications.domain.repository.NotificationLogRepository;
import com.unihub.workshop.notifications.infrastructure.email.EmailSenderProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreationNotificationConsumer {

    private static final String EVENT_TYPE = "UserAccountCreated";

    private final EmailSenderProvider emailSenderProvider;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(UserAccountCreatedEvent event) {
        NotificationLog logEntry = createQueuedLog(event);
        notificationLogRepository.save(logEntry);

        try {
            String subject = "Welcome to UniHub";
            String body = buildHtmlBody(event);
            emailSenderProvider.sendEmail(event.email(), subject, body);

            logEntry.setStatus(NotificationStatus.SENT);
            logEntry.setStatusAt(OffsetDateTime.now());
            notificationLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.error("Failed to send account creation email to {}", event.email(), ex);
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setStatusAt(OffsetDateTime.now());
            logEntry.setErrorMessage(ex.getMessage());
            notificationLogRepository.save(logEntry);
        }
    }

    private NotificationLog createQueuedLog(UserAccountCreatedEvent event) {
        NotificationLog logEntry = new NotificationLog();
        logEntry.setEventType(EVENT_TYPE);
        logEntry.setChannel(NotificationChannel.EMAIL);
        logEntry.setStatus(NotificationStatus.QUEUED);
        logEntry.setRecipientAddress(event.email());
        logEntry.setStatusAt(OffsetDateTime.now());
        logEntry.setPayload(buildPayload(event));
        return logEntry;
    }

    private String buildHtmlBody(UserAccountCreatedEvent event) {
        return """
                <html>
                  <body style="font-family:Arial,sans-serif;line-height:1.6;color:#1f2937;">
                    <h2>Welcome to UniHub</h2>
                    <p>Hello %s,</p>
                    <p>Your account has been created with the role <strong>%s</strong>.</p>
                    <p>Your temporary password is: <strong>%s</strong></p>
                    <p>Please log in and change it immediately.</p>
                  </body>
                </html>
                """.formatted(event.fullName(), event.role(), event.rawTemporaryPassword());
    }

    private String buildPayload(UserAccountCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "email", event.email(),
                    "fullName", event.fullName(),
                    "role", event.role()));
        } catch (Exception ex) {
            log.warn("Failed to serialize account creation notification payload for {}", event.email(), ex);
            return "{}";
        }
    }
}
