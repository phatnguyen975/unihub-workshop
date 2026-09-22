package com.unihub.workshop.iam.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.unihub.workshop.shared.exception.BadRequestException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository repository;
    private final UserPersistenceMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    @Transactional
    public Optional<User> findById(UUID id) {
        return repository.findById(id).map(userMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByIdAndOrganizationId(
        UUID id,
        UUID organizationId
    ) {
        return repository
            .findByIdAndOrganizationId(id, organizationId)
            .map(userMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(userMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    @Transactional
    public User save(User user) {
        UserJpaEntity entity = userMapper.toEntity(user);
        UserJpaEntity saved = repository.save(entity);
        return userMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public void updateLastLoginAt(UUID userId, Instant lastLoginAt) {
        repository.updateLastLoginAt(userId, lastLoginAt);
    }

    @Override
    @Transactional
    public void activate(UUID userId) {
        repository.activateUser(userId, Instant.now());
    }

    @Override
    @Transactional
    public boolean updateTemporaryPassword(
        UUID userId,
        String newPasswordHash,
        Instant loginTime
    ) {
        return (
            repository.updateTemporaryPassword(
                userId,
                newPasswordHash,
                loginTime
            ) > 0
        );
    }

    @Override
    @Transactional
    public void updateIsActive(
        UUID userId,
        UUID organizationId,
        boolean isActive
    ) {
        repository.updateIsActive(
            userId,
            organizationId,
            isActive,
            Instant.now()
        );
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
        int size
    ) {
        Instant cursorTime = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                String trimmedCursor = cursor.trim();
                String decoded = new String(
                    Base64.getDecoder().decode(trimmedCursor)
                );
                CursorData cursorData = objectMapper.readValue(
                    decoded,
                    CursorData.class
                );
                if (
                    cursorData == null ||
                    cursorData.createdAt() == null ||
                    cursorData.id() == null
                ) {
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
        String sanitizedSearch = sanitizeSearch(search);
        PageRequest pageable = PageRequest.of(0, size + 1);
        List<UserJpaEntity> entities =
            cursorTime == null
                ? repository.findFirstPage(
                      organizationId,
                      callerId,
                      roleStr,
                      isActive,
                      sanitizedSearch,
                      pageable
                  )
                : repository.findNextPage(
                      organizationId,
                      callerId,
                      roleStr,
                      isActive,
                      sanitizedSearch,
                      cursorTime,
                      cursorId,
                      pageable
                  );

        boolean hasMore = entities.size() > size;
        List<UserJpaEntity> pageEntities = hasMore
            ? entities.subList(0, size)
            : entities;

        List<User> items = userMapper.toDomainList(pageEntities);

        String nextCursor = null;
        if (hasMore && !pageEntities.isEmpty()) {
            UserJpaEntity lastItem = pageEntities.get(pageEntities.size() - 1);
            try {
                CursorData nextCursorData = new CursorData(
                    lastItem.getCreatedAt(),
                    lastItem.getId()
                );
                String json = objectMapper.writeValueAsString(nextCursorData);
                nextCursor = Base64.getEncoder().encodeToString(
                    json.getBytes()
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode next cursor", e);
            }
        }

        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private record CursorData(Instant createdAt, UUID id) {}

    private String sanitizeSearch(String search) {
        if (search == null) {
            return null;
        }
        return search
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
