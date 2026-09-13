package com.unihub.workshop.iam.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentDirectoryJpaRepository
    extends JpaRepository<StudentDirectoryJpaEntity, UUID>
{
    Optional<StudentDirectoryJpaEntity> findByEmailAndStatus(
        String email,
        String status
    );
}
