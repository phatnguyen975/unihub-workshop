package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopPersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WorkshopRepositoryAdapter implements WorkshopRepository {

    private final WorkshopJpaRepository repository;
    private final WorkshopPersistenceMapper mapper;

    @Override
    @Transactional
    public Workshop save(Workshop workshop) {
        WorkshopJpaEntity entity = mapper.toEntity(workshop);
        try {
            WorkshopJpaEntity saved = repository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException(
                "VERSION_CONFLICT",
                "Workshop version conflict occurred while saving: " +
                    ex.getMessage()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workshop> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workshop> findByIdAndOrganizationId(
        UUID id,
        UUID organizationId
    ) {
        return repository
            .findByIdAndOrganizationId(id, organizationId)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        return repository.existsBySlug(slug);
    }
}
