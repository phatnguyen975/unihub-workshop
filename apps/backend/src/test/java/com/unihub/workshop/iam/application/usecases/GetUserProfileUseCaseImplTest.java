package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetUserProfileUseCaseImplTest {

    private UserRepository userRepository;
    private GetUserProfileUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        useCase = new GetUserProfileUseCaseImpl(userRepository);
    }

    @Test
    void shouldReturnUserProfile_whenUserExists() {
        UUID userId = UUID.randomUUID();
        User expectedUser = new User(
                userId,
                UUID.randomUUID(),
                UserRole.STUDENT,
                "alice@university.edu",
                "+84912345678",
                "hash",
                "Alice Nguyen",
                "https://cdn.unihub.vn/avatar.jpg",
                true,
                false,
                Instant.now(),
                UUID.randomUUID(),
                Instant.now(),
                Instant.now(),
                null,
                null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(expectedUser));

        User actualUser = useCase.getProfile(userId);

        assertThat(actualUser).isEqualTo(expectedUser);
    }

    @Test
    void shouldThrowUnauthorizedException_whenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getProfile(userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("User not found");
    }

    @Test
    void shouldThrowUnauthorizedException_whenUserIdIsNull() {
        assertThatThrownBy(() -> useCase.getProfile(null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("User not found");
    }
}
