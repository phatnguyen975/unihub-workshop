package com.unihub.workshop.workers.notifications.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.notifications.core.models.DeliveryChannel;
import com.unihub.workshop.workers.notifications.core.models.DeliveryStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryLogAdapterTest {

    @Mock
    private JpaNotificationDeliveryLogRepository repository;

    private NotificationDeliveryLogAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationDeliveryLogAdapter(repository);
    }

    @Test
    @DisplayName("Should check if message was already sent by idempotency key")
    void shouldCheckIfAlreadySent() {
        when(
            repository.existsByRecipientAddressAndChannelAndStatusAndProviderMessageId(
                "student@hcmus.edu.vn",
                "EMAIL",
                "SENT",
                "KEY-123"
            )
        ).thenReturn(true);

        boolean exists = adapter.isAlreadySent(
            "student@hcmus.edu.vn",
            DeliveryChannel.EMAIL,
            "KEY-123"
        );
        assertTrue(exists);

        when(
            repository.existsByRecipientAddressAndChannelAndStatusAndProviderMessageId(
                "student@hcmus.edu.vn",
                "EMAIL",
                "SENT",
                "KEY-999"
            )
        ).thenReturn(false);

        boolean notExists = adapter.isAlreadySent(
            "student@hcmus.edu.vn",
            DeliveryChannel.EMAIL,
            "KEY-999"
        );
        assertFalse(notExists);
    }

    @Test
    @DisplayName("Should create pending log record and return generated ID")
    void shouldCreatePendingLog() {
        UUID userId = UUID.randomUUID();
        UUID generatedId = UUID.randomUUID();

        when(
            repository.save(any(NotificationDeliveryLogEntity.class))
        ).thenAnswer(inv -> {
            NotificationDeliveryLogEntity entity = inv.getArgument(0);
            entity.setId(generatedId);
            return entity;
        });

        UUID resultId = adapter.createPendingLog(
            userId,
            DeliveryChannel.EMAIL,
            "student@hcmus.edu.vn",
            "KEY-123"
        );

        assertEquals(generatedId, resultId);
        ArgumentCaptor<NotificationDeliveryLogEntity> captor =
            ArgumentCaptor.forClass(NotificationDeliveryLogEntity.class);
        verify(repository).save(captor.capture());

        NotificationDeliveryLogEntity saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals("EMAIL", saved.getChannel());
        assertEquals("student@hcmus.edu.vn", saved.getRecipientAddress());
        assertEquals(DeliveryStatus.PENDING.name(), saved.getStatus());
        assertEquals("KEY-123", saved.getProviderMessageId());
    }

    @Test
    @DisplayName(
        "Should transition pending log to SENT with provider message ID and sent_at timestamp"
    )
    void shouldMarkSent() {
        UUID logId = UUID.randomUUID();
        NotificationDeliveryLogEntity entity =
            NotificationDeliveryLogEntity.builder()
                .id(logId)
                .status(DeliveryStatus.PENDING.name())
                .build();

        when(repository.findById(logId)).thenReturn(Optional.of(entity));

        adapter.markSent(logId, "PROVIDER-MSG-1");

        assertEquals(DeliveryStatus.SENT.name(), entity.getStatus());
        assertEquals("PROVIDER-MSG-1", entity.getProviderMessageId());
        assertNotNull(entity.getSentAt());
        verify(repository).save(entity);
    }

    @Test
    @DisplayName("Should transition pending log to FAILED with error reason")
    void shouldMarkFailed() {
        UUID logId = UUID.randomUUID();
        NotificationDeliveryLogEntity entity =
            NotificationDeliveryLogEntity.builder()
                .id(logId)
                .status(DeliveryStatus.PENDING.name())
                .build();

        when(repository.findById(logId)).thenReturn(Optional.of(entity));

        adapter.markFailed(logId, "Connection refused");

        assertEquals(DeliveryStatus.FAILED.name(), entity.getStatus());
        assertEquals("Connection refused", entity.getErrorReason());
        assertNotNull(entity.getSentAt());
        verify(repository).save(entity);
    }
}
