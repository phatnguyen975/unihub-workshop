package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.FilterFingerprint;
import com.unihub.workshop.shared.pagination.JpaKeysetPagination;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository repository;
    private final UserPersistenceMapper userMapper;
    private final EntityManager entityManager;

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
        String q,
        String cursor,
        int size
    ) {
        String normalizedQuery =
            q != null && !q.isBlank()
                ? sanitizeQuery(q.trim().toLowerCase(Locale.ROOT))
                : null;
        String filterFingerprint = FilterFingerprint.builder()
            .with("role", role)
            .with("isActive", isActive)
            .with("q", normalizedQuery)
            .build();
        KeysetCursor cursorData = KeysetCursorCodec.decode(
            cursor,
            "createdAt",
            filterFingerprint
        );
        if (cursorData != null && cursorData.asInstant() == null) {
            throw new BadRequestException("Invalid cursor format");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UserJpaEntity> cq = cb.createQuery(UserJpaEntity.class);
        Root<UserJpaEntity> root = cq.from(UserJpaEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("organizationId"), organizationId));
        if (callerId != null) {
            predicates.add(cb.notEqual(root.get("id"), callerId));
        }
        if (role != null) {
            predicates.add(cb.equal(root.get("role"), role.name()));
        }
        if (isActive != null) {
            predicates.add(cb.equal(root.get("isActive"), isActive));
        }
        if (normalizedQuery != null) {
            String pattern = "%" + normalizedQuery + "%";
            Predicate nameMatch = cb.like(
                cb.lower(root.get("fullName")),
                pattern,
                '\\'
            );
            Predicate emailMatch = cb.like(
                cb.lower(root.get("email")),
                pattern,
                '\\'
            );
            Predicate phoneMatch = cb.and(
                cb.isNotNull(root.get("phoneNumber")),
                cb.like(cb.lower(root.get("phoneNumber")), pattern, '\\')
            );
            predicates.add(cb.or(nameMatch, emailMatch, phoneMatch));
        }

        Path<Instant> createdAtPath = root.get("createdAt");
        Path<UUID> idPath = root.get("id");

        if (cursorData != null) {
            predicates.add(
                JpaKeysetPagination.buildKeysetPredicate(
                    cb,
                    createdAtPath,
                    cursorData.asInstant(),
                    true,
                    createdAtPath,
                    cursorData.asInstant(),
                    idPath,
                    cursorData.id()
                )
            );
        }

        cq.where(predicates.toArray(new Predicate[0]));
        JpaKeysetPagination.applyOrders(
            cb,
            cq,
            createdAtPath,
            true,
            null,
            idPath
        );

        JpaKeysetPagination.KeysetSlice<UserJpaEntity> slice =
            JpaKeysetPagination.execute(entityManager, cq, size);

        List<User> items = userMapper.toDomainList(slice.items());

        String nextCursor = null;
        if (slice.hasMore() && !slice.items().isEmpty()) {
            UserJpaEntity lastItem = slice
                .items()
                .get(slice.items().size() - 1);
            nextCursor = KeysetCursorCodec.encode(
                new KeysetCursor(
                    lastItem.getId(),
                    "createdAt",
                    lastItem.getCreatedAt().toString(),
                    null,
                    filterFingerprint
                )
            );
        }

        return new CursorPage<>(items, nextCursor, slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAllById(java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository
            .findAllById(ids)
            .stream()
            .map(userMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findActiveStaffByOrganizationId(UUID organizationId) {
        if (organizationId == null) {
            return List.of();
        }
        return repository
            .findByOrganizationIdAndRoleAndIsActiveTrue(
                organizationId,
                UserRole.STAFF.name()
            )
            .stream()
            .map(userMapper::toDomain)
            .toList();
    }

    private String sanitizeQuery(String query) {
        if (query == null) {
            return null;
        }
        return query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
