package com.unihub.workshop.iam.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentDirectoryJpaRepository extends JpaRepository<StudentDirectoryJpaEntity, UUID> {
    Optional<StudentDirectoryJpaEntity> findByEmailAndStatus(String email, String status);
}
