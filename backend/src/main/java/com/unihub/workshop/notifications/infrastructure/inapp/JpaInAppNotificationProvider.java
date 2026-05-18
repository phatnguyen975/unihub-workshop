package com.unihub.workshop.notifications.infrastructure.inapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.notifications.domain.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JpaInAppNotificationProvider implements InAppNotificationProvider {

    private final InAppNotificationRepository inAppNotificationRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void sendInAppNotification(UUID userId, String title, String message, String type, UUID referenceId) {
        InAppNotification notification = new InAppNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setBody(message);
        notification.setEventType(type);
        notification.setPayload(buildPayload(referenceId));
        inAppNotificationRepository.save(notification);
        log.debug("Created in-app notification for user={} type={} referenceId={}", userId, type, referenceId);
    }

    private String buildPayload(UUID referenceId) {
        try {
            return objectMapper.writeValueAsString(Map.of("referenceId", referenceId));
        } catch (Exception ex) {
            log.warn("Failed to serialize in-app notification payload for referenceId={}", referenceId, ex);
            return "{}";
        }
    }
}
