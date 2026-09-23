package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.in.RevokeAllOtherSessionsUseCase;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

class ChangeUserPasswordUseCaseImplTest {

    private UserRepository userRepository;
    private RevokeAllOtherSessionsUseCase revokeAllOtherSessionsUseCase;
    private PasswordEncoder passwordEncoder;
    private MeterRegistry meterRegistry;
    private Counter counter;

    private ChangeUserPasswordUseCaseImpl useCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CURRENT_SESSION_ID = UUID.randomUUID();
    private static final String STORED_HASH =
        "$argon2id$v=19$m=65536,t=3,p=1$storedhash";
    private static final String CURRENT_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456@";
    private static final String NEW_HASH =
        "$argon2id$v=19$m=65536,t=3,p=1$newhash";

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        revokeAllOtherSessionsUseCase = mock(
            RevokeAllOtherSessionsUseCase.class
        );
        passwordEncoder = mock(PasswordEncoder.class);
        meterRegistry = mock(MeterRegistry.class);
        counter = mock(Counter.class);

        when(
            meterRegistry.counter(anyString(), any(String[].class))
        ).thenReturn(counter);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

        useCase = new ChangeUserPasswordUseCaseImpl(
            userRepository,
            revokeAllOtherSessionsUseCase,
            passwordEncoder,
            meterRegistry
        );
    }

    private User createSampleUser() {
        return new User(
            USER_ID,
            null,
            UserRole.STUDENT,
            "alice@university.edu",
            "+84912345678",
            STORED_HASH,
            "Alice Smith",
            null,
            true,
            false,
            Instant.now(),
            null,
            Instant.now().minusSeconds(86400),
            Instant.now().minusSeconds(86400),
            null,
            null
        );
    }

    @Test
    void shouldChangePasswordSuccessfully_whenValidInput() {
        User user = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT_PASSWORD, STORED_HASH)).thenReturn(
            true
        );

        useCase.changePassword(
            USER_ID,
            CURRENT_SESSION_ID,
            CURRENT_PASSWORD,
            NEW_PASSWORD
        );

        InOrder inOrder = inOrder(
            revokeAllOtherSessionsUseCase,
            userRepository
        );
        inOrder
            .verify(revokeAllOtherSessionsUseCase)
            .revokeAllOtherSessions(USER_ID, CURRENT_SESSION_ID);
        inOrder
            .verify(userRepository)
            .save(
                argThat(
                    u ->
                        u.passwordHash().equals(NEW_HASH) &&
                        u.updatedBy().equals(USER_ID)
                )
            );

        verify(counter).increment();
    }

    @Test
    void shouldFailClosedAndNotMutateDb_whenRevocationFails() {
        User user = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT_PASSWORD, STORED_HASH)).thenReturn(
            true
        );

        doThrow(
            new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis down"
            )
        )
            .when(revokeAllOtherSessionsUseCase)
            .revokeAllOtherSessions(USER_ID, CURRENT_SESSION_ID);

        assertThatThrownBy(() ->
            useCase.changePassword(
                USER_ID,
                CURRENT_SESSION_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
            )
        ).isInstanceOf(ServiceUnavailableException.class);

        verify(userRepository, never()).save(any());
        verify(counter, never()).increment();
    }

    @Test
    void shouldThrowValidationException_whenCurrentPasswordDoesNotMatch() {
        User user = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(
            passwordEncoder.matches("WrongPassword123!", STORED_HASH)
        ).thenReturn(false);

        assertThatThrownBy(() ->
            useCase.changePassword(
                USER_ID,
                CURRENT_SESSION_ID,
                "WrongPassword123!",
                NEW_PASSWORD
            )
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Current password is incorrect.");

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowValidationException_whenNewPasswordSameAsCurrentPassword() {
        User user = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT_PASSWORD, STORED_HASH)).thenReturn(
            true
        );

        assertThatThrownBy(() ->
            useCase.changePassword(
                USER_ID,
                CURRENT_SESSION_ID,
                CURRENT_PASSWORD,
                CURRENT_PASSWORD
            )
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("New password must be different");

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowValidationException_whenNewPasswordIsWeak() {
        User user = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT_PASSWORD, STORED_HASH)).thenReturn(
            true
        );

        assertThatThrownBy(() ->
            useCase.changePassword(
                USER_ID,
                CURRENT_SESSION_ID,
                CURRENT_PASSWORD,
                "short"
            )
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Password must be between 8 and 255 characters"
            );

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowUnauthorizedException_whenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            useCase.changePassword(
                USER_ID,
                CURRENT_SESSION_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
            )
        )
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("User not found");

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowUnauthorizedException_whenAccountIsDeactivated() {
        User user = new User(
            USER_ID,
            null,
            UserRole.STUDENT,
            "alice@university.edu",
            "+84912345678",
            STORED_HASH,
            "Alice Smith",
            null,
            false, // deactivated
            false,
            Instant.now(),
            null,
            Instant.now().minusSeconds(86400),
            Instant.now().minusSeconds(86400),
            null,
            null
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            useCase.changePassword(
                USER_ID,
                CURRENT_SESSION_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
            )
        )
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("Account is deactivated");

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowUnauthorizedException_whenUserIdIsNull() {
        assertThatThrownBy(() ->
            useCase.changePassword(
                null,
                CURRENT_SESSION_ID,
                CURRENT_PASSWORD,
                NEW_PASSWORD
            )
        )
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("User not found");

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowValidationException_whenCurrentPasswordIsNull() {
        User user = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            useCase.changePassword(
                USER_ID,
                CURRENT_SESSION_ID,
                null,
                NEW_PASSWORD
            )
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Current password is incorrect.");

        verifyNoInteractions(revokeAllOtherSessionsUseCase);
        verify(userRepository, never()).save(any());
    }
}
