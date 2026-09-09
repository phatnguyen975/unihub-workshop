package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.in.ListMembersQuery;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListOrganizationMembersUseCaseImplTest {

    private UserRepository userRepository;
    private ListOrganizationMembersUseCaseImpl useCase;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        useCase = new ListOrganizationMembersUseCaseImpl(userRepository);
    }

    @Test
    void shouldThrowBadRequest_whenQueryIsNull() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Query must not be null");
    }

    @Test
    void shouldThrowBadRequest_whenOrganizationIdIsNull() {
        ListMembersQuery query = new ListMembersQuery(null, CALLER_ID, null, null, null, null, 20);
        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("organizationId must not be null");
    }

    @Test
    void shouldThrowBadRequest_whenCallerIdIsNull() {
        ListMembersQuery query = new ListMembersQuery(ORG_ID, null, null, null, null, null, 20);
        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("callerId must not be null");
    }

    @Test
    void shouldThrowBadRequest_whenLimitIsLessThanOne() {
        ListMembersQuery query = new ListMembersQuery(ORG_ID, CALLER_ID, null, null, null, null, 0);
        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    @Test
    void shouldThrowBadRequest_whenLimitIsGreaterThan100() {
        ListMembersQuery query = new ListMembersQuery(ORG_ID, CALLER_ID, null, null, null, null, 101);
        assertThatThrownBy(() -> useCase.execute(query))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    @Test
    void shouldDefaultLimitTo20_whenLimitIsNull() {
        ListMembersQuery query = new ListMembersQuery(ORG_ID, CALLER_ID, null, null, null, null, null);

        when(userRepository.findMembersByOrganizationId(eq(ORG_ID), eq(CALLER_ID), isNull(), isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(new CursorPage<>(List.of(), null, false));

        CursorPage<User> result = useCase.execute(query);

        assertThat(result).isNotNull();
        assertThat(result.items()).isEmpty();
        verify(userRepository).findMembersByOrganizationId(ORG_ID, CALLER_ID, null, null, null, null, 20);
    }

    @Test
    void shouldTrimSearchAndPassToRepository_whenSearchHasWhitespace() {
        ListMembersQuery query = new ListMembersQuery(ORG_ID, CALLER_ID, UserRole.STAFF, true, "  alice  ", "cursor123", 15);

        User member = new User(
                UUID.randomUUID(), ORG_ID, UserRole.STAFF, "alice@test.org", null, true, false, Instant.now());
        when(userRepository.findMembersByOrganizationId(ORG_ID, CALLER_ID, UserRole.STAFF, true, "alice", "cursor123", 15))
                .thenReturn(new CursorPage<>(List.of(member), "next-cursor", true));

        CursorPage<User> result = useCase.execute(query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).email()).isEqualTo("alice@test.org");
        assertThat(result.nextCursor()).isEqualTo("next-cursor");
        assertThat(result.hasMore()).isTrue();
        verify(userRepository).findMembersByOrganizationId(ORG_ID, CALLER_ID, UserRole.STAFF, true, "alice", "cursor123", 15);
    }

    @Test
    void shouldTreatBlankSearchAsNull() {
        ListMembersQuery query = new ListMembersQuery(ORG_ID, CALLER_ID, null, null, "   ", null, 20);

        when(userRepository.findMembersByOrganizationId(ORG_ID, CALLER_ID, null, null, null, null, 20))
                .thenReturn(new CursorPage<>(List.of(), null, false));

        useCase.execute(query);

        verify(userRepository).findMembersByOrganizationId(ORG_ID, CALLER_ID, null, null, null, null, 20);
    }
}
