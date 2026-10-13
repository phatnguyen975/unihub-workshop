package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.workshops.application.ports.out.AiSummaryJobRepository;
import com.unihub.workshop.workshops.domain.model.AiSummaryJob;
import com.unihub.workshop.workshops.domain.model.AiSummaryJobStatus;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.AiSummaryJobPersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiSummaryJobRepositoryAdapter implements AiSummaryJobRepository {

    private final AiSummaryJobJpaRepository jpaRepository;
    private final AiSummaryJobPersistenceMapper mapper;

    @Override
    public AiSummaryJob save(AiSummaryJob job) {
        AiSummaryJobJpaEntity entity = mapper.toEntity(job);
        AiSummaryJobJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public boolean existsByWorkshopIdAndStatus(
        UUID workshopId,
        AiSummaryJobStatus status
    ) {
        return jpaRepository.existsByWorkshopIdAndStatus(
            workshopId,
            status.name()
        );
    }

    @Override
    public Optional<AiSummaryJob> findLatestByWorkshopId(UUID workshopId) {
        return jpaRepository
            .findFirstByWorkshopIdOrderByCreatedAtDesc(workshopId)
            .map(mapper::toDomain);
    }
}
