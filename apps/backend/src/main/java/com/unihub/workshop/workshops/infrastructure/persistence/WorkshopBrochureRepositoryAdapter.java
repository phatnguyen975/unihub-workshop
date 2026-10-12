package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.workshops.application.ports.out.WorkshopBrochureRepository;
import com.unihub.workshop.workshops.domain.model.WorkshopBrochure;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopBrochurePersistenceMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkshopBrochureRepositoryAdapter
    implements WorkshopBrochureRepository
{

    private final WorkshopBrochureJpaRepository jpaRepository;
    private final WorkshopBrochurePersistenceMapper mapper;

    @Override
    public WorkshopBrochure save(WorkshopBrochure brochure) {
        WorkshopBrochureJpaEntity entity = mapper.toEntity(brochure);
        WorkshopBrochureJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<WorkshopBrochure> findActiveByWorkshopId(UUID workshopId) {
        return jpaRepository
            .findByWorkshopIdAndIsActiveTrue(workshopId)
            .map(mapper::toDomain);
    }

    @Override
    public void deactivateAllByWorkshopId(UUID workshopId) {
        jpaRepository.deactivateAllByWorkshopId(workshopId, Instant.now());
    }
}
