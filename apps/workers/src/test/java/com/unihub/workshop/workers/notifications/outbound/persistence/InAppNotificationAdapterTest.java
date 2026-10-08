package com.unihub.workshop.workers.notifications.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InAppNotificationAdapterTest {

    @Mock
    private JpaInAppNotificationRepository repository;

    private ObjectMapper objectMapper;
    private InAppNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new InAppNotificationAdapter(repository, objectMapper);
    }

    @Test
    @DisplayName(
        "Should check whether in-app notification exists with idempotencyKey"
    )
    void shouldCheckIsAlreadyCreated() {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "STAFF_ASSIGNED:U-1:W-1";

        when(
            repository.existsByUserIdAndIdempotencyKey(userId, idempotencyKey)
        ).thenReturn(true);

        assertTrue(adapter.isAlreadyCreated(userId, idempotencyKey));
        assertFalse(adapter.isAlreadyCreated(null, idempotencyKey));
        assertFalse(adapter.isAlreadyCreated(userId, null));
    }

    @Test
    @DisplayName(
        "Should serialize payload with idempotencyKey and save InAppNotificationEntity"
    )
    void shouldSaveInAppNotification() {
        UUID userId = UUID.randomUUID();
        String eventKey = "STAFF_ASSIGNED_TO_WORKSHOP";
        String actionUrl = "/workshops/W-1";
        String idempotencyKey = "STAFF_ASSIGNED:" + userId + ":W-1";
        Map<String, Object> payload = Map.of("workshopTitle", "Spring Cloud");

        adapter.createNotification(
            userId,
            eventKey,
            actionUrl,
            payload,
            idempotencyKey
        );

        verify(repository).save(
            argThat(
                entity ->
                    entity.getUserId().equals(userId) &&
                    entity.getEventKey().equals(eventKey) &&
                    entity.getActionUrl().equals(actionUrl) &&
                    !entity.isRead() &&
                    entity.getPayload().contains("idempotencyKey") &&
                    entity.getPayload().contains("Spring Cloud")
            )
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if serialization fails")
    void shouldThrowIfSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(
            new JsonProcessingException("Serialization failed") {}
        );

        InAppNotificationAdapter failingAdapter = new InAppNotificationAdapter(
            repository,
            failingMapper
        );

        assertThrows(IllegalArgumentException.class, () ->
            failingAdapter.createNotification(
                UUID.randomUUID(),
                "KEY",
                "/url",
                Map.of(),
                "IDEMP"
            )
        );
    }
}
