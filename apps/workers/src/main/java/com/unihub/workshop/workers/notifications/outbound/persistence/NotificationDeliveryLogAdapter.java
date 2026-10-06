package com.unihub.workshop.workers.notifications.outbound.persistence;

import com.unihub.workshop.workers.notifications.core.models.DeliveryChannel;
import com.unihub.workshop.workers.notifications.core.models.DeliveryStatus;
import com.unihub.workshop.workers.notifications.core.ports.out.NotificationDeliveryLogPort;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeliveryLogAdapter
    implements NotificationDeliveryLogPort
{

    private final JpaNotificationDeliveryLogRepository repository;

    @Override
    @Transactional(readOnly = true)
    public boolean isAlreadySent(
        String recipientAddress,
        DeliveryChannel channel,
        String idempotencyKey
    ) {
        return repository.existsByRecipientAddressAndChannelAndStatusAndProviderMessageId(
            recipientAddress,
            channel.name(),
            DeliveryStatus.SENT.name(),
            idempotencyKey
        );
    }

    @Override
    @Transactional
    public UUID createPendingLog(
        UUID userId,
        DeliveryChannel channel,
        String recipientAddress,
        String idempotencyKey
    ) {
        NotificationDeliveryLogEntity entity =
            NotificationDeliveryLogEntity.builder()
                .userId(userId)
                .channel(channel.name())
                .recipientAddress(recipientAddress)
                .status(DeliveryStatus.PENDING.name())
                .providerMessageId(idempotencyKey)
                .build();

        NotificationDeliveryLogEntity saved = repository.save(entity);
        return saved.getId();
    }

    @Override
    @Transactional
    public void markSent(UUID logId, String providerMessageId) {
        repository.findById(logId).ifPresent(entity -> {
            entity.setStatus(DeliveryStatus.SENT.name());
            entity.setSentAt(Instant.now());
            if (providerMessageId != null) {
                entity.setProviderMessageId(providerMessageId);
            }
            repository.save(entity);
        });
    }

    @Override
    @Transactional
    public void markFailed(UUID logId, String errorReason) {
        repository.findById(logId).ifPresent(entity -> {
            entity.setStatus(DeliveryStatus.FAILED.name());
            entity.setErrorReason(errorReason);
            entity.setSentAt(Instant.now());
            repository.save(entity);
        });
    }
}
