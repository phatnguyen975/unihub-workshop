package com.unihub.workshop.notifications.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNotificationRepository
    extends
        JpaRepository<InAppNotificationJpaEntity, UUID>,
        JpaSpecificationExecutor<InAppNotificationJpaEntity>
{
    @Query(
        """
        SELECT COUNT(n) FROM InAppNotificationJpaEntity n
        WHERE n.userId = :userId AND n.isRead = false
        """
    )
    long countByUserIdAndIsReadFalse(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE InAppNotificationJpaEntity n
        SET n.isRead = true, n.readAt = :readAt, n.updatedAt = :readAt
        WHERE n.userId = :userId AND n.isRead = false
        """
    )
    int markAllAsReadByUserId(
        @Param("userId") UUID userId,
        @Param("readAt") Instant readAt
    );
}
