package com.unihub.workshop.workshops.infrastructure.persistence;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopPersistenceMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
    private final WorkshopSpeakerJpaRepository speakerRepository;
    private final WorkshopPersistenceMapper mapper;

    @Override
    @Transactional
    public Workshop save(Workshop workshop) {
        WorkshopJpaEntity entity = mapper.toEntity(workshop);
        try {
            WorkshopJpaEntity saved = repository.saveAndFlush(entity);

            if (workshop.speakers() != null) {
                speakerRepository.deleteByWorkshopId(saved.getId());
                if (!workshop.speakers().isEmpty()) {
                    List<WorkshopSpeakerJpaEntity> speakerEntities = workshop
                        .speakers()
                        .stream()
                        .map(s -> {
                            WorkshopSpeakerJpaEntity se =
                                mapper.toSpeakerEntity(s);
                            se.setWorkshopId(saved.getId());
                            if (se.getId() == null) {
                                se.setId(UUID.randomUUID());
                            }
                            if (se.getCreatedBy() == null) {
                                se.setCreatedBy(saved.getCreatedBy());
                            }
                            if (se.getUpdatedBy() == null) {
                                se.setUpdatedBy(saved.getUpdatedBy());
                            }
                            if (se.getCreatedAt() == null) {
                                se.setCreatedAt(Instant.now());
                            }
                            if (se.getUpdatedAt() == null) {
                                se.setUpdatedAt(Instant.now());
                            }
                            return se;
                        })
                        .toList();
                    speakerRepository.saveAllAndFlush(speakerEntities);
                }
            }

            return mapToDomainWithSpeakers(saved);
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException(
                "Workshop version conflict occurred while saving: " +
                    ex.getMessage()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workshop> findById(UUID id) {
        return repository.findById(id).map(this::mapToDomainWithSpeakers);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workshop> findByIdAndOrganizationId(
        UUID id,
        UUID organizationId
    ) {
        return repository
            .findByIdAndOrganizationId(id, organizationId)
            .map(this::mapToDomainWithSpeakers);
    }

    private Workshop mapToDomainWithSpeakers(WorkshopJpaEntity entity) {
        List<WorkshopSpeakerJpaEntity> speakers =
            speakerRepository.findByWorkshopIdOrderBySortOrderAsc(
                entity.getId()
            );
        return mapper
            .toDomain(entity)
            .toBuilder()
            .speakers(mapper.toSpeakerDomainList(speakers))
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String slug) {
        return repository.existsBySlug(slug);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findConflictingSpeakerEmails(
        Collection<String> emails,
        Instant startsAt,
        Instant endsAt,
        UUID excludeWorkshopId
    ) {
        if (
            emails == null ||
            emails.isEmpty() ||
            startsAt == null ||
            endsAt == null
        ) {
            return List.of();
        }
        List<String> normalizedEmails = emails
            .stream()
            .filter(e -> e != null && !e.isBlank())
            .map(String::trim)
            .map(String::toLowerCase)
            .toList();

        if (normalizedEmails.isEmpty()) {
            return List.of();
        }

        if (excludeWorkshopId != null) {
            return speakerRepository.findConflictingSpeakerEmailsExcludingWorkshop(
                normalizedEmails,
                startsAt,
                endsAt,
                excludeWorkshopId
            );
        } else {
            return speakerRepository.findConflictingSpeakerEmails(
                normalizedEmails,
                startsAt,
                endsAt
            );
        }
    }
}
