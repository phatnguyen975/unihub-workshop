package com.unihub.workshop.iam.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository repository;
    private final ObjectMapper objectMapper;

    public UserRepositoryAdapter(UserJpaRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return repository.findByIdAndOrganizationId(id, organizationId).map(this::toDomain);
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

    @Override
    @Transactional(readOnly = true)
    public CursorPage<User> findMembersByOrganizationId(
            UUID organizationId,
            UUID callerId,
            UserRole role,
            Boolean isActive,
            String search,
            String cursor,
            int limit) {
        Instant cursorTime = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(cursor));
                CursorData cursorData = objectMapper.readValue(decoded, CursorData.class);
                if (cursorData == null || cursorData.createdAt() == null || cursorData.id() == null) {
                    throw new BadRequestException("Invalid cursor format");
                }
                cursorTime = cursorData.createdAt();
                cursorId = cursorData.id();
            } catch (Exception e) {
                if (e instanceof BadRequestException) {
                    throw (BadRequestException) e;
                }
                throw new BadRequestException("Invalid cursor format");
            }
        }

        String roleStr = role != null ? role.name() : null;
        List<UserJpaEntity> entities = repository.findMembers(
                organizationId,
                callerId,
                roleStr,
                isActive,
                search,
                cursorTime,
                cursorId,
                PageRequest.of(0, limit + 1));

        boolean hasMore = entities.size() > limit;
        List<UserJpaEntity> pageEntities = hasMore ? entities.subList(0, limit) : entities;

        List<User> items = pageEntities.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasMore && !pageEntities.isEmpty()) {
            UserJpaEntity lastItem = pageEntities.get(pageEntities.size() - 1);
            try {
                CursorData nextCursorData = new CursorData(lastItem.getCreatedAt(), lastItem.getId());
                String json = objectMapper.writeValueAsString(nextCursorData);
                nextCursor = Base64.getEncoder().encodeToString(json.getBytes());
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode next cursor", e);
            }
        }

        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private record CursorData(Instant createdAt, UUID id) {
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
