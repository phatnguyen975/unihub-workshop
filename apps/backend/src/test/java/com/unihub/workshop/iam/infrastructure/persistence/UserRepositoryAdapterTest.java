package com.unihub.workshop.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserPersistenceMapperImpl;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserRepositoryAdapterTest {

    private final UserPersistenceMapper userMapper =
        new UserPersistenceMapperImpl();
    private UserJpaRepository jpaRepo;
    private EntityManager entityManager;
    private UserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepo = mock(UserJpaRepository.class);
        entityManager = mock(EntityManager.class);
        adapter = new UserRepositoryAdapter(jpaRepo, userMapper, entityManager);
    }

    @Test
    void shouldFindUserByEmail() {
        UUID userId = UUID.randomUUID();
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(userId);
        entity.setEmail("student@test.com");
        entity.setRole(UserRole.STUDENT.name());
        entity.setPasswordHash("hash");
        entity.setActive(false);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(jpaRepo.findByEmail("student@test.com")).thenReturn(
            Optional.of(entity)
        );

        Optional<User> result = adapter.findByEmail("student@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(userId);
        assertThat(result.get().email()).isEqualTo("student@test.com");
        assertThat(result.get().isActive()).isFalse();
    }

    @Test
    void shouldFindByIdAndOrganizationId() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(userId);
        entity.setOrganizationId(orgId);
        entity.setEmail("member@test.org");
        entity.setRole(UserRole.STAFF.name());
        entity.setPasswordHash("hash");
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(jpaRepo.findByIdAndOrganizationId(userId, orgId)).thenReturn(
            Optional.of(entity)
        );

        Optional<User> result = adapter.findByIdAndOrganizationId(
            userId,
            orgId
        );

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(userId);
        assertThat(result.get().organizationId()).isEqualTo(orgId);
        assertThat(result.get().email()).isEqualTo("member@test.org");
    }

    @Test
    void shouldActivateUser() {
        UUID userId = UUID.randomUUID();
        adapter.activate(userId);

        verify(jpaRepo).activateUser(eq(userId), any(Instant.class));
    }

    @Test
    void shouldFindMembersByOrganizationId_withoutCursor_returnsItemsAndNextCursorWhenHasMore() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        Instant t1 = Instant.parse("2026-09-04T10:00:00Z");
        Instant t2 = Instant.parse("2026-09-04T09:00:00Z");
        Instant t3 = Instant.parse("2026-09-04T08:00:00Z");

        UserJpaEntity e1 = createMemberEntity(
            id1,
            orgId,
            "STAFF",
            "staff1@test.org",
            t1
        );
        UserJpaEntity e2 = createMemberEntity(
            id2,
            orgId,
            "STUDENT",
            "student1@test.org",
            t2
        );
        UserJpaEntity e3 = createMemberEntity(
            id3,
            orgId,
            "STAFF",
            "staff2@test.org",
            t3
        );

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<UserJpaEntity> cq = mock();
        Root<UserJpaEntity> root = mock();
        TypedQuery<UserJpaEntity> typedQuery = mock();
        Path<Object> orgPath = mock();
        Path<Object> idPath = mock();
        Path<Object> createdAtPath = mock();

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(UserJpaEntity.class)).thenReturn(cq);
        when(cq.from(UserJpaEntity.class)).thenReturn(root);

        lenient().when(root.get("organizationId")).thenReturn(orgPath);
        lenient().when(root.get("id")).thenReturn(idPath);
        lenient().when(root.get("createdAt")).thenReturn(createdAtPath);
        lenient()
            .when(cb.equal(any(), any()))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.notEqual(any(), any()))
            .thenReturn(mock(Predicate.class));

        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(3)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(e1, e2, e3));

        CursorPage<User> page = adapter.findMembersByOrganizationId(
            orgId,
            callerId,
            null,
            null,
            null,
            null,
            2
        );

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).id()).isEqualTo(id1);
        assertThat(page.items().get(1).id()).isEqualTo(id2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotNull();

        String decoded = new String(
            Base64.getDecoder().decode(page.nextCursor())
        );
        assertThat(decoded).contains(id2.toString());
    }

    @Test
    void shouldFindMembersByOrganizationId_withValidCursor_decodesAndQueriesRepository() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID cursorId = UUID.randomUUID();
        Instant cursorTime = Instant.parse("2026-09-04T09:00:00Z");

        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                cursorId,
                "createdAt",
                cursorTime.toString(),
                null,
                null
            )
        );

        UUID id3 = UUID.randomUUID();
        Instant t3 = Instant.parse("2026-09-04T08:00:00Z");
        UserJpaEntity e3 = createMemberEntity(
            id3,
            orgId,
            "STAFF",
            "staff2@test.org",
            t3
        );

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<UserJpaEntity> cq = mock();
        Root<UserJpaEntity> root = mock();
        TypedQuery<UserJpaEntity> typedQuery = mock();
        Path<Object> orgPath = mock();
        Path<Object> idPath = mock();
        Path<Object> createdAtPath = mock();
        Path<Object> rolePath = mock();
        Path<Object> activePath = mock();
        Path<String> fullNamePath = mock();
        Path<String> emailPath = mock();
        Path<String> phonePath = mock();

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(UserJpaEntity.class)).thenReturn(cq);
        when(cq.from(UserJpaEntity.class)).thenReturn(root);

        lenient().when(root.get("organizationId")).thenReturn(orgPath);
        lenient().when(root.get("id")).thenReturn(idPath);
        lenient().when(root.get("createdAt")).thenReturn(createdAtPath);
        lenient().when(root.get("role")).thenReturn(rolePath);
        lenient().when(root.get("isActive")).thenReturn(activePath);
        lenient().when(root.<String>get("fullName")).thenReturn(fullNamePath);
        lenient().when(root.<String>get("email")).thenReturn(emailPath);
        lenient().when(root.<String>get("phoneNumber")).thenReturn(phonePath);

        lenient()
            .when(cb.equal(any(), any()))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.notEqual(any(), any()))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.like(any(), anyString(), anyChar()))
            .thenReturn(mock(Predicate.class));
        lenient().when(cb.lower(any())).thenReturn(mock());
        lenient().when(cb.isNotNull(any())).thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.and(any(Predicate.class), any(Predicate.class)))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.or(any(Predicate.class), any(Predicate.class)))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.or(any(Predicate[].class)))
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

        CursorPage<User> page = adapter.findMembersByOrganizationId(
            orgId,
            callerId,
            UserRole.STAFF,
            true,
            "staff",
            cursor,
            2
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).id()).isEqualTo(id3);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldThrowBadRequestException_whenCursorIsMalformed() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        assertThatThrownBy(() ->
            adapter.findMembersByOrganizationId(
                orgId,
                callerId,
                null,
                null,
                null,
                "invalid-base64",
                20
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Invalid cursor format");
    }

    @Test
    void shouldFindMembersByOrganizationId_escapesWildcardCharactersInSearch() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<UserJpaEntity> cq = mock();
        Root<UserJpaEntity> root = mock();
        TypedQuery<UserJpaEntity> typedQuery = mock();

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(UserJpaEntity.class)).thenReturn(cq);
        when(cq.from(UserJpaEntity.class)).thenReturn(root);

        Path<Object> orgPath = mock();
        Path<Object> idPath = mock();
        Path<Object> createdAtPath = mock();
        Path<String> fullNamePath = mock();
        Path<String> emailPath = mock();
        Path<String> phonePath = mock();

        lenient().when(root.get("organizationId")).thenReturn(orgPath);
        lenient().when(root.get("id")).thenReturn(idPath);
        lenient().when(root.get("createdAt")).thenReturn(createdAtPath);
        lenient().when(root.<String>get("fullName")).thenReturn(fullNamePath);
        lenient().when(root.<String>get("email")).thenReturn(emailPath);
        lenient().when(root.<String>get("phoneNumber")).thenReturn(phonePath);

        lenient()
            .when(cb.equal(any(), any()))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.notEqual(any(), any()))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.like(any(), anyString(), anyChar()))
            .thenReturn(mock(Predicate.class));
        lenient().when(cb.lower(any())).thenReturn(mock());
        lenient().when(cb.isNotNull(any())).thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.and(any(Predicate.class), any(Predicate.class)))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.or(any(Predicate[].class)))
            .thenReturn(mock(Predicate.class));

        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(21)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());

        CursorPage<User> page = adapter.findMembersByOrganizationId(
            orgId,
            callerId,
            null,
            null,
            "100%_test\\user",
            null,
            20
        );

        assertThat(page.items()).isEmpty();
        verify(cb, times(3)).like(
            any(),
            eq("%100\\%\\_test\\\\user%"),
            eq('\\')
        );
    }

    @Test
    void shouldRejectCursorBoundToAnotherFilterSet() {
        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                UUID.randomUUID(),
                "createdAt",
                "2026-10-21T05:12:00Z",
                null,
                "role=STUDENT&isActive=*&q=*"
            )
        );

        assertThatThrownBy(() ->
            adapter.findMembersByOrganizationId(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UserRole.ORGANIZER,
                null,
                null,
                cursor,
                20
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("different filter set");
    }

    private UserJpaEntity createMemberEntity(
        UUID id,
        UUID orgId,
        String role,
        String email,
        Instant createdAt
    ) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(id);
        entity.setOrganizationId(orgId);
        entity.setRole(role);
        entity.setEmail(email);
        entity.setFullName("Test User " + id);
        entity.setPasswordHash("hash");
        entity.setActive(true);
        entity.setTemporaryPassword(false);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(createdAt);
        return entity;
    }
}
