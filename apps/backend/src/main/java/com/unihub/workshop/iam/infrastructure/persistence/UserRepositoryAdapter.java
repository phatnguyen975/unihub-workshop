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
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        UserJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void updateLastLoginAt(UUID userId, Instant lastLoginAt) {
        repository.updateLastLoginAt(userId, lastLoginAt);
    }

    @Override
    public void activate(UUID userId) {
        repository.activateUser(userId, Instant.now());
    }

    @Override
    public boolean updateTemporaryPassword(UUID userId, String newPasswordHash, Instant loginTime) {
        return repository.updateTemporaryPassword(userId, newPasswordHash, loginTime) > 0;
    }

    private User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(),
                entity.getOrganizationId(),
                UserRole.valueOf(entity.getRole()),
                entity.getEmail(),
                entity.getPhoneNumber(),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.getAvatarUrl(),
                entity.isActive(),
                entity.isTemporaryPassword(),
                entity.getLastLoginAt(),
                entity.getStudentRecordId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy());
    }

    private UserJpaEntity toEntity(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(user.id());
        entity.setOrganizationId(user.organizationId());
        entity.setRole(user.role().name());
        entity.setEmail(user.email());
        entity.setPhoneNumber(user.phoneNumber());
        entity.setPasswordHash(user.passwordHash());
        entity.setFullName(user.fullName());
        entity.setAvatarUrl(user.avatarUrl());
        entity.setActive(user.isActive());
        entity.setTemporaryPassword(user.isTemporaryPassword());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setStudentRecordId(user.studentRecordId());
        entity.setCreatedAt(user.createdAt() != null ? user.createdAt() : Instant.now());
        entity.setUpdatedAt(user.updatedAt() != null ? user.updatedAt() : Instant.now());
        entity.setCreatedBy(user.createdBy());
        entity.setUpdatedBy(user.updatedBy());
        return entity;
    }
}
