package com.unihub.workshop.notifications.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.iam.domain.events.PasswordResetRequestedEvent;
import com.unihub.workshop.iam.domain.events.UserRegisteredEvent;
import com.unihub.workshop.iam.domain.repository.OtpStore;
import com.unihub.workshop.notifications.domain.model.NotificationChannel;
import com.unihub.workshop.notifications.domain.model.NotificationLog;
import com.unihub.workshop.notifications.domain.model.NotificationStatus;
import com.unihub.workshop.notifications.domain.repository.NotificationLogRepository;
import com.unihub.workshop.notifications.infrastructure.email.EmailSenderProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpNotificationConsumer {

    private static final String REGISTER_PURPOSE = "REGISTER";
    private static final String RESET_PURPOSE = "RESET_PASSWORD";

    private final OtpStore otpStore;
    private final EmailSenderProvider emailSenderProvider;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(UserRegisteredEvent event) {
        sendOtpEmail(event.email(), REGISTER_PURPOSE, "OTP registration email");
    }

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PasswordResetRequestedEvent event) {
        sendOtpEmail(event.email(), RESET_PURPOSE, "OTP password reset email");
    }

    private void sendOtpEmail(String email, String purpose, String eventType) {
        String otpCode = otpStore.getOtp(email);
        if (otpCode == null || otpCode.isBlank()) {
            log.error("Failed to load OTP for email={} purpose={}", email, purpose);
            NotificationLog failedLog = createLog(email, purpose, eventType, null, NotificationStatus.FAILED,
                    "OTP code not found");
            notificationLogRepository.save(failedLog);
            return;
        }

        NotificationLog logEntry = createLog(email, purpose, eventType, otpCode, NotificationStatus.QUEUED, null);
        notificationLogRepository.save(logEntry);

        try {
            String subject = switch (purpose) {
                case REGISTER_PURPOSE -> "UniHub Registration OTP";
                case RESET_PURPOSE -> "UniHub Password Reset OTP";
                default -> "UniHub OTP";
            };
            String htmlBody = "Your OTP code is: <b>" + otpCode + "</b>";
            emailSenderProvider.sendEmail(email, subject, htmlBody);

            logEntry.setStatus(NotificationStatus.SENT);
            logEntry.setStatusAt(java.time.OffsetDateTime.now());
            notificationLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.error("Failed to send OTP email to {} for purpose {}", email, purpose, ex);
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setStatusAt(java.time.OffsetDateTime.now());
            logEntry.setErrorMessage(ex.getMessage());
            notificationLogRepository.save(logEntry);
        }
    }

    private NotificationLog createLog(String email, String purpose, String eventType, String otpCode,
            NotificationStatus status, String errorMessage) {
        NotificationLog logEntry = new NotificationLog();
        logEntry.setRecipientAddress(email);
        logEntry.setChannel(NotificationChannel.EMAIL);
        logEntry.setStatus(status);
        logEntry.setEventType(eventType);
        logEntry.setPayload(buildPayload(email, purpose, otpCode));
        logEntry.setErrorMessage(errorMessage);
        return logEntry;
    }

    private String buildPayload(String email, String purpose, String otpCode) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "email", email,
                    "purpose", purpose,
                    "otpCode", otpCode));
        } catch (Exception ex) {
            log.warn("Could not serialize notification payload for email={} purpose={}", email, purpose, ex);
            return "{}";
        }
    }
}
