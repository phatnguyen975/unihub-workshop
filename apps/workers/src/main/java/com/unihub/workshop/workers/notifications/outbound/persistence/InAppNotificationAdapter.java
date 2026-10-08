package com.unihub.workshop.workers.notifications.outbound.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.workers.notifications.core.ports.out.InAppNotificationPort;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InAppNotificationAdapter implements InAppNotificationPort {

    private final JpaInAppNotificationRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public boolean isAlreadyCreated(UUID userId, String idempotencyKey) {
        if (userId == null || idempotencyKey == null) {
            return false;
        }
        return repository.existsByUserIdAndIdempotencyKey(
            userId,
            idempotencyKey
        );
    }

    @Override
    @Transactional
    public void createNotification(
        UUID userId,
        String eventKey,
        String actionUrl,
        Map<String, Object> payload,
        String idempotencyKey
    ) {
        Map<String, Object> enrichedPayload = new HashMap<>(
            payload != null ? payload : Map.of()
        );
        if (idempotencyKey != null) {
            enrichedPayload.put("idempotencyKey", idempotencyKey);
        }

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(enrichedPayload);
        } catch (JsonProcessingException e) {
            log.error(
                "Failed to serialize notification payload to JSON for userId='{}', eventKey='{}'",
                userId,
                eventKey,
                e
            );
            throw new IllegalArgumentException(
                "Failed to serialize notification payload to JSON",
                e
            );
        }

        InAppNotificationEntity entity = InAppNotificationEntity.builder()
            .userId(userId)
            .eventKey(eventKey)
            .actionUrl(actionUrl)
            .payload(jsonPayload)
            .isRead(false)
            .build();

        repository.save(entity);
        log.info(
            "Persisted in-app notification for userId='{}', eventKey='{}', actionUrl='{}'",
            userId,
            eventKey,
            actionUrl
        );
    }
}
