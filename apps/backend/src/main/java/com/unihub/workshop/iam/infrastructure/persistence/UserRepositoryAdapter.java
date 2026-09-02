package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository repository;

    public UserRepositoryAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(entity -> new User(
                entity.getId(),
                entity.getOrganizationId(),
                UserRole.valueOf(entity.getRole()),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.isActive(),
                entity.isTemporaryPassword(),
                entity.getLastLoginAt()));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(entity -> new User(
                entity.getId(),
                entity.getOrganizationId(),
                UserRole.valueOf(entity.getRole()),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.isActive(),
                entity.isTemporaryPassword(),
                entity.getLastLoginAt()));
    }

    @Override
    public void updateLastLoginAt(UUID userId, Instant lastLoginAt) {
        repository.updateLastLoginAt(userId, lastLoginAt);
    }
}
