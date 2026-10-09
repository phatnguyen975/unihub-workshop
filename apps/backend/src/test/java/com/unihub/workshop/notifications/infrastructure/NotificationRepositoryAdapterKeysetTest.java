package com.unihub.workshop.notifications.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.notifications.infrastructure.persistence.InAppNotificationJpaEntity;
import com.unihub.workshop.notifications.infrastructure.persistence.NotificationRepositoryAdapter;
import com.unihub.workshop.notifications.infrastructure.persistence.SpringDataNotificationRepository;
import com.unihub.workshop.notifications.infrastructure.persistence.mapper.NotificationPersistenceMapper;
import com.unihub.workshop.notifications.infrastructure.persistence.mapper.NotificationPersistenceMapperImpl;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationRepositoryAdapterKeysetTest {

    private SpringDataNotificationRepository springDataRepository;
    private EntityManager entityManager;
    private NotificationPersistenceMapper mapper;
    private NotificationRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        springDataRepository = mock(SpringDataNotificationRepository.class);
        entityManager = mock(EntityManager.class);
        mapper = new NotificationPersistenceMapperImpl();
        adapter = new NotificationRepositoryAdapter(
            springDataRepository,
            entityManager,
            mapper
        );
    }

    private InAppNotificationJpaEntity createEntity(
        UUID id,
        UUID userId,
        String eventKey,
        boolean isRead,
        Instant createdAt
    ) {
        return InAppNotificationJpaEntity.builder()
            .id(id)
            .userId(userId)
            .eventKey(eventKey)
            .payload("{\"key\":\"value\"}")
            .isRead(isRead)
            .readAt(isRead ? createdAt.plusSeconds(60) : null)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    }

    @Test
    @DisplayName(
        "should query notifications without cursor and return items and next cursor when hasMore is true"
    )
    @SuppressWarnings("unchecked")
    void shouldFindNotifications_withoutCursor_returnsItemsAndNextCursorWhenHasMore() {
        UUID userId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        Instant t1 = Instant.parse("2026-10-08T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-08T09:00:00Z");
        Instant t3 = Instant.parse("2026-10-08T08:00:00Z");

        InAppNotificationJpaEntity e1 = createEntity(
            id1,
            userId,
            "EVT_1",
            false,
            t1
        );
        InAppNotificationJpaEntity e2 = createEntity(
            id2,
            userId,
            "EVT_2",
            true,
            t2
        );
        InAppNotificationJpaEntity e3 = createEntity(
            id3,
            userId,
            "EVT_3",
            false,
            t3
        );

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<InAppNotificationJpaEntity> cq = mock(
            CriteriaQuery.class
        );
        Root<InAppNotificationJpaEntity> root = mock(Root.class);
        TypedQuery<InAppNotificationJpaEntity> typedQuery = mock(
            TypedQuery.class
        );
        Path<Object> userPath = mock(Path.class);
        Path<Instant> createdAtPath = mock(Path.class);
        Path<UUID> idPath = mock(Path.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(InAppNotificationJpaEntity.class)).thenReturn(cq);
        when(cq.from(InAppNotificationJpaEntity.class)).thenReturn(root);

        lenient().when(root.get("userId")).thenReturn(userPath);
        lenient()
            .when(root.<Instant>get("createdAt"))
            .thenReturn(createdAtPath);
        lenient().when(root.<UUID>get("id")).thenReturn(idPath);
        lenient()
            .when(cb.equal(any(), any()))
            .thenReturn(mock(Predicate.class));

        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(3)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(e1, e2, e3));

        CursorPage<InAppNotification> page = adapter.findNotificationsByUserId(
            userId,
            null,
            null,
            2
        );

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).getId()).isEqualTo(id1);
        assertThat(page.items().get(1).getId()).isEqualTo(id2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotNull();

        String decoded = new String(
            Base64.getDecoder().decode(page.nextCursor())
        );
        assertThat(decoded).contains(id2.toString());
        assertThat(decoded).contains("createdAt");
    }

    @Test
    @DisplayName("should query notifications with isRead filter applied")
    @SuppressWarnings("unchecked")
    void shouldFindNotifications_withIsReadFilter() {
        UUID userId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-10-08T10:00:00Z");
        InAppNotificationJpaEntity e1 = createEntity(
            id1,
            userId,
            "EVT_1",
            false,
            t1
        );

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<InAppNotificationJpaEntity> cq = mock(
            CriteriaQuery.class
        );
        Root<InAppNotificationJpaEntity> root = mock(Root.class);
        TypedQuery<InAppNotificationJpaEntity> typedQuery = mock(
            TypedQuery.class
        );
        Path<Object> userPath = mock(Path.class);
        Path<Object> isReadPath = mock(Path.class);
        Path<Instant> createdAtPath = mock(Path.class);
        Path<UUID> idPath = mock(Path.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(InAppNotificationJpaEntity.class)).thenReturn(cq);
        when(cq.from(InAppNotificationJpaEntity.class)).thenReturn(root);

        lenient().when(root.get("userId")).thenReturn(userPath);
        lenient().when(root.get("isRead")).thenReturn(isReadPath);
        lenient()
            .when(root.<Instant>get("createdAt"))
            .thenReturn(createdAtPath);
        lenient().when(root.<UUID>get("id")).thenReturn(idPath);
        lenient()
            .when(cb.equal(any(), any()))
            .thenReturn(mock(Predicate.class));

        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(21)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(e1));

        CursorPage<InAppNotification> page = adapter.findNotificationsByUserId(
            userId,
            false,
            null,
            20
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getId()).isEqualTo(id1);
        assertThat(page.items().get(0).isRead()).isFalse();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("should query notifications with valid cursor")
    @SuppressWarnings("unchecked")
    void shouldFindNotifications_withValidCursor() {
        UUID userId = UUID.randomUUID();
        UUID cursorId = UUID.randomUUID();
        Instant cursorTime = Instant.parse("2026-10-08T09:00:00Z");
        String cursor = KeysetCursorCodec.encode(
            KeysetCursor.of(cursorId, "createdAt", cursorTime.toString())
        );

        UUID id3 = UUID.randomUUID();
        Instant t3 = Instant.parse("2026-10-08T08:00:00Z");
        InAppNotificationJpaEntity e3 = createEntity(
            id3,
            userId,
            "EVT_3",
            false,
            t3
        );

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<InAppNotificationJpaEntity> cq = mock(
            CriteriaQuery.class
        );
        Root<InAppNotificationJpaEntity> root = mock(Root.class);
        TypedQuery<InAppNotificationJpaEntity> typedQuery = mock(
            TypedQuery.class
        );
        Path<Object> userPath = mock(Path.class);
        Path<Instant> createdAtPath = mock(Path.class);
        Path<UUID> idPath = mock(Path.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(InAppNotificationJpaEntity.class)).thenReturn(cq);
        when(cq.from(InAppNotificationJpaEntity.class)).thenReturn(root);

        lenient().when(root.get("userId")).thenReturn(userPath);
        lenient()
            .when(root.<Instant>get("createdAt"))
            .thenReturn(createdAtPath);
        lenient().when(root.<UUID>get("id")).thenReturn(idPath);
        lenient()
            .when(cb.equal(any(), any()))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.or(any(Predicate.class), any(Predicate.class)))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.and(any(Predicate.class), any(Predicate.class)))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.lessThan(any(), any(Instant.class)))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.lessThan(any(), any(UUID.class)))
            .thenReturn(mock(Predicate.class));

        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(3)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(e3));

        CursorPage<InAppNotification> page = adapter.findNotificationsByUserId(
            userId,
            null,
            cursor,
            2
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getId()).isEqualTo(id3);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("should throw BadRequestException when cursor is malformed")
    void shouldThrowBadRequestException_whenCursorIsMalformed() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() ->
            adapter.findNotificationsByUserId(
                userId,
                null,
                "invalid-base64",
                20
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Invalid cursor format");
    }

    @Test
    @DisplayName(
        "should throw BadRequestException when cursor sort field mismatches"
    )
    void shouldThrowBadRequestException_whenCursorSortFieldMismatches() {
        UUID userId = UUID.randomUUID();
        String mismatchedCursor = KeysetCursorCodec.encode(
            KeysetCursor.of(UUID.randomUUID(), "id", "some-value")
        );

        assertThatThrownBy(() ->
            adapter.findNotificationsByUserId(
                userId,
                null,
                mismatchedCursor,
                20
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Cursor was issued for sort='id'");
    }
}
