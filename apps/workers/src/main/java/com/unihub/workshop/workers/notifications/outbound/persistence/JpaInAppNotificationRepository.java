package com.unihub.workshop.workers.notifications.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaInAppNotificationRepository
    extends JpaRepository<InAppNotificationEntity, UUID>
{
    boolean existsByUserIdAndPayloadContaining(
        UUID userId,
        String idempotencyKey
    );
}
