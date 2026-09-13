package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetOrganizationMemberUseCaseImplTest {

    private UserRepository userRepository;
    private GetOrganizationMemberUseCaseImpl useCase;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        useCase = new GetOrganizationMemberUseCaseImpl(userRepository);
    }

    @Test
    void shouldThrowBadRequest_whenOrganizationIdIsNull() {
        assertThatThrownBy(() -> useCase.execute(null, USER_ID))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("organizationId must not be null");
    }

    @Test
    void shouldThrowBadRequest_whenMemberUserIdIsNull() {
        assertThatThrownBy(() -> useCase.execute(ORG_ID, null))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("memberUserId must not be null");
    }

    @Test
    void shouldThrowNotFound_whenMemberNotFoundInOrganization() {
        when(
            userRepository.findByIdAndOrganizationId(USER_ID, ORG_ID)
        ).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG_ID, USER_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Member not found in your organization.");
    }

    @Test
    void shouldReturnUser_whenMemberExistsInOrganization() {
        Instant now = Instant.now();
        User member = new User(
            USER_ID,
            ORG_ID,
            UserRole.STAFF,
            "alice@techclub.org",
            "+84901234567",
            "hash",
            "Alice Nguyen",
            null,
            true,
            false,
            now,
            null,
            now,
            now,
            null,
            null
        );

        when(
            userRepository.findByIdAndOrganizationId(USER_ID, ORG_ID)
        ).thenReturn(Optional.of(member));

        User result = useCase.execute(ORG_ID, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(USER_ID);
        assertThat(result.organizationId()).isEqualTo(ORG_ID);
        assertThat(result.email()).isEqualTo("alice@techclub.org");
        assertThat(result.role()).isEqualTo(UserRole.STAFF);
    }
}
