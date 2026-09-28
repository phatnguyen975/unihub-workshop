package com.unihub.workshop.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.UserPersistenceMapperImpl;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class UserRepositoryAdapterTest {

    private final UserPersistenceMapper userMapper =
        new UserPersistenceMapperImpl();

    @Test
    void shouldFindUserByEmail() {
        UserJpaRepository jpaRepo = mock(UserJpaRepository.class);
        UserRepositoryAdapter adapter = new UserRepositoryAdapter(
            jpaRepo,
            userMapper
        );

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
        UserJpaRepository jpaRepo = mock(UserJpaRepository.class);
        UserRepositoryAdapter adapter = new UserRepositoryAdapter(
            jpaRepo,
            userMapper
        );

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
        UserJpaRepository jpaRepo = mock(UserJpaRepository.class);
        UserRepositoryAdapter adapter = new UserRepositoryAdapter(
            jpaRepo,
            userMapper
        );

        UUID userId = UUID.randomUUID();
        adapter.activate(userId);

        verify(jpaRepo).activateUser(eq(userId), any(Instant.class));
    }

    @Test
    void shouldFindMembersByOrganizationId_withoutCursor_returnsItemsAndNextCursorWhenHasMore() {
        UserJpaRepository jpaRepo = mock(UserJpaRepository.class);
        UserRepositoryAdapter adapter = new UserRepositoryAdapter(
            jpaRepo,
            userMapper
        );

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

        when(
            jpaRepo.findFirstPage(
                eq(orgId),
                eq(callerId),
                isNull(),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 3))
            )
        ).thenReturn(List.of(e1, e2, e3));

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
            java.util.Base64.getDecoder().decode(page.nextCursor())
        );
        assertThat(decoded).contains(id2.toString());
    }

    @Test
    void shouldFindMembersByOrganizationId_withValidCursor_decodesAndQueriesRepository()
        throws Exception {
        UserJpaRepository jpaRepo = mock(UserJpaRepository.class);
        UserRepositoryAdapter adapter = new UserRepositoryAdapter(
            jpaRepo,
            userMapper
        );

        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID cursorId = UUID.randomUUID();
        Instant cursorTime = Instant.parse("2026-09-04T09:00:00Z");

        ObjectMapper mapper = new ObjectMapper().registerModule(
            new JavaTimeModule()
        );
        String cursorJson = mapper.writeValueAsString(
            new UserCursor(cursorTime, cursorId)
        );
        String cursor = java.util.Base64.getEncoder().encodeToString(
            cursorJson.getBytes()
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

        when(
            jpaRepo.findNextPage(
                eq(orgId),
                eq(callerId),
                eq("STAFF"),
                eq(true),
                eq("staff"),
                eq(cursorTime),
                eq(cursorId),
                eq(PageRequest.of(0, 3))
            )
        ).thenReturn(List.of(e3));

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
        UserJpaRepository jpaRepo = mock(UserJpaRepository.class);
        UserRepositoryAdapter adapter = new UserRepositoryAdapter(
            jpaRepo,
            userMapper
        );

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
        UserJpaRepository jpaRepo = mock(UserJpaRepository.class);
        UserRepositoryAdapter adapter = new UserRepositoryAdapter(
            jpaRepo,
            userMapper
        );

        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        when(
            jpaRepo.findFirstPage(
                eq(orgId),
                eq(callerId),
                isNull(),
                isNull(),
                eq("100\\%\\_test\\\\user"),
                eq(PageRequest.of(0, 21))
            )
        ).thenReturn(List.of());

        adapter.findMembersByOrganizationId(
            orgId,
            callerId,
            null,
            null,
            "100%_test\\user",
            null,
            20
        );

        verify(jpaRepo).findFirstPage(
            eq(orgId),
            eq(callerId),
            isNull(),
            isNull(),
            eq("100\\%\\_test\\\\user"),
            eq(PageRequest.of(0, 21))
        );
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

    private record UserCursor(Instant createdAt, UUID id) {}
}
