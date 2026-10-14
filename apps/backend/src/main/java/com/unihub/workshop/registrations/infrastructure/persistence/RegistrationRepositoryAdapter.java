package com.unihub.workshop.registrations.infrastructure.persistence;

import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.infrastructure.persistence.mapper.RegistrationPersistenceMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RegistrationRepositoryAdapter implements RegistrationRepository {

    private final RegistrationJpaRepository repository;
    private final RegistrationPersistenceMapper mapper;

    @Override
    @Transactional
    public Registration save(Registration registration) {
        RegistrationJpaEntity entity = mapper.toEntity(registration);
        RegistrationJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Registration> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveRegistration(
        UUID studentUserId,
        UUID workshopId
    ) {
        if (studentUserId == null || workshopId == null) {
            return false;
        }
        return repository.existsActiveRegistration(studentUserId, workshopId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOverlappingActiveRegistration(
        UUID studentUserId,
        Instant startsAt,
        Instant endsAt
    ) {
        if (studentUserId == null || startsAt == null || endsAt == null) {
            return false;
        }
        return repository.hasOverlappingActiveRegistration(
            studentUserId,
            startsAt,
            endsAt
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveRegistrations(UUID workshopId) {
        if (workshopId == null) {
            return 0L;
        }
        return repository.countActiveRegistrations(workshopId);
    }
}
