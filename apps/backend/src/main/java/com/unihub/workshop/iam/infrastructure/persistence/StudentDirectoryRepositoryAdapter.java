package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.StudentDirectoryRepository;
import com.unihub.workshop.iam.domain.model.StudentDirectory;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.StudentDirectoryPersistenceMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudentDirectoryRepositoryAdapter
    implements StudentDirectoryRepository
{

    private final StudentDirectoryJpaRepository repository;
    private final StudentDirectoryPersistenceMapper studentDirectoryMapper;

    @Override
    public Optional<StudentDirectory> findActiveByEmail(String email) {
        return repository
            .findByEmailAndStatus(email, "ACTIVE")
            .map(studentDirectoryMapper::toDomain);
    }
}
