package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.StudentDirectoryRepository;
import com.unihub.workshop.iam.domain.model.StudentDirectory;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.StudentDirectoryPersistenceMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StudentDirectoryRepositoryAdapter
    implements StudentDirectoryRepository
{

    private final StudentDirectoryJpaRepository repository;
    private final StudentDirectoryPersistenceMapper studentDirectoryMapper;

    @Override
    @Transactional
    public Optional<StudentDirectory> findActiveByEmail(String email) {
        return repository
            .findByEmailAndStatus(email, "ACTIVE")
            .map(studentDirectoryMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentDirectory> findAllByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository
            .findAllById(ids)
            .stream()
            .map(studentDirectoryMapper::toDomain)
            .toList();
    }
}
