package com.unihub.workshop.workers.notifications.outbound.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaInAppNotificationRepository
    extends JpaRepository<InAppNotificationEntity, UUID>
{
    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM in_app_notifications
            WHERE user_id = :userId
                AND payload ->> 'idempotencyKey' = :idempotencyKey
        )
        """,
        nativeQuery = true
    )
    boolean existsByUserIdAndIdempotencyKey(
        @Param("userId") UUID userId,
        @Param("idempotencyKey") String idempotencyKey
    );
}
