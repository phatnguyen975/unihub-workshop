package com.unihub.workshop.notifications.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}
