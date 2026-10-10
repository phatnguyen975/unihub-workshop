package com.unihub.workshop.notifications.infrastructure.persistence;

import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.notifications.infrastructure.persistence.mapper.NotificationPersistenceMapper;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
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
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final SpringDataNotificationRepository springDataNotificationRepository;
    private final EntityManager entityManager;
    private final NotificationPersistenceMapper notificationPersistenceMapper;

    @Override
    @Transactional(readOnly = true)
    public long countUnreadByUserId(UUID userId) {
        return springDataNotificationRepository.countByUserIdAndIsReadFalse(
            userId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<InAppNotification> findNotificationsByUserId(
        UUID userId,
        Boolean isRead,
        String cursor,
        int size
    ) {
        KeysetCursor cursorData = KeysetCursorCodec.decode(cursor, "createdAt");
        if (cursorData != null && cursorData.asInstant() == null) {
            throw new BadRequestException("Invalid cursor format");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<InAppNotificationJpaEntity> cq = cb.createQuery(
            InAppNotificationJpaEntity.class
        );
        Root<InAppNotificationJpaEntity> root = cq.from(
            InAppNotificationJpaEntity.class
        );

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("userId"), userId));
        if (isRead != null) {
            predicates.add(cb.equal(root.get("isRead"), isRead));
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

        JpaKeysetPagination.KeysetSlice<InAppNotificationJpaEntity> slice =
            JpaKeysetPagination.execute(entityManager, cq, size);

        List<InAppNotification> items =
            notificationPersistenceMapper.toDomainList(slice.items());

        String nextCursor = null;
        if (slice.hasMore() && !slice.items().isEmpty()) {
            InAppNotificationJpaEntity lastItem = slice
                .items()
                .get(slice.items().size() - 1);
            nextCursor = KeysetCursorCodec.encode(
                KeysetCursor.of(
                    lastItem.getId(),
                    "createdAt",
                    lastItem.getCreatedAt().toString()
                )
            );
        }

        return new CursorPage<>(items, nextCursor, slice.hasMore());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InAppNotification> findById(UUID id) {
        return springDataNotificationRepository
            .findById(id)
            .map(notificationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public InAppNotification save(InAppNotification notification) {
        InAppNotificationJpaEntity entity =
            notificationPersistenceMapper.toEntity(notification);
        InAppNotificationJpaEntity saved =
            springDataNotificationRepository.save(entity);
        return notificationPersistenceMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public int markAllAsReadByUserId(UUID userId, Instant readAt) {
        return springDataNotificationRepository.markAllAsReadByUserId(
            userId,
            readAt
        );
    }
}
