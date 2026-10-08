package com.unihub.workshop.workers.notifications.outbound.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {
    @Query(
        """
        SELECT u FROM UserEntity u
        WHERE u.organizationId = :organizationId
            AND u.role = 'ORGANIZER'
            AND u.isActive = true
        """
    )
    List<UserEntity> findActiveOrganizersByOrganizationId(
        @Param("organizationId") UUID organizationId
    );
}
