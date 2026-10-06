package com.unihub.workshop.workers.notifications.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface JpaNotificationDeliveryLogRepository
    extends JpaRepository<NotificationDeliveryLogEntity, UUID>
{
    boolean existsByRecipientAddressAndChannelAndStatusAndProviderMessageId(
        String recipientAddress,
        String channel,
        String status,
        String providerMessageId
    );
}
