package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.StudentDirectoryRepository;
import com.unihub.workshop.iam.domain.model.StudentDirectory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StudentDirectoryRepositoryAdapter implements StudentDirectoryRepository {

    private final StudentDirectoryJpaRepository repository;

    public StudentDirectoryRepositoryAdapter(StudentDirectoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StudentDirectory> findActiveByEmail(String email) {
        return repository.findByEmailAndStatus(email, "ACTIVE").map(this::toDomain);
    }

    private StudentDirectory toDomain(StudentDirectoryJpaEntity entity) {
        return new StudentDirectory(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getStudentNumber(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getFaculty(),
                entity.getStatus(),
                entity.getSourceFileKey(),
                entity.getImportedAt(),
                entity.getCreatedAt());
    }
}
