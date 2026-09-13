package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileCommand;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UpdateUserProfileUseCaseImplTest {

    private UserRepository userRepository;
    private UpdateUserProfileUseCaseImpl useCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID STUDENT_RECORD_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        useCase = new UpdateUserProfileUseCaseImpl(userRepository);
    }

    private User createSampleUser() {
        return new User(
            USER_ID,
            ORG_ID,
            UserRole.STUDENT,
            "student@university.edu",
            "+84912345678",
            "hash",
            "Alice Smith",
            "https://cdn.unihub.vn/avatar.jpg",
            true,
            false,
            Instant.now(),
            STUDENT_RECORD_ID,
            Instant.now().minusSeconds(3600),
            Instant.now().minusSeconds(3600),
            null,
            null
        );
    }

    @Test
    void shouldUpdateFullNameOnly_whenOnlyFullNameProvided() {
        User existing = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(
            Optional.of(existing)
        );
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
            "Alice Thu Nguyen",
            true,
            null,
            false,
            null,
            false
        );

        User result = useCase.updateProfile(USER_ID, command);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.fullName()).isEqualTo("Alice Thu Nguyen");
        assertThat(saved.phoneNumber()).isEqualTo("+84912345678");
        assertThat(saved.avatarUrl()).isEqualTo(
            "https://cdn.unihub.vn/avatar.jpg"
        );
        assertThat(saved.organizationId()).isEqualTo(ORG_ID);
        assertThat(saved.studentRecordId()).isEqualTo(STUDENT_RECORD_ID);
        assertThat(saved.updatedBy()).isEqualTo(USER_ID);

        assertThat(result.fullName()).isEqualTo("Alice Thu Nguyen");
    }

    @Test
    void shouldClearPhoneNumber_whenPhoneNumberProvidedAsNull() {
        User existing = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(
            Optional.of(existing)
        );
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
            null,
            false,
            null,
            true,
            null,
            false
        );

        useCase.updateProfile(USER_ID, command);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.fullName()).isEqualTo("Alice Smith");
        assertThat(saved.phoneNumber()).isNull();
        assertThat(saved.avatarUrl()).isEqualTo(
            "https://cdn.unihub.vn/avatar.jpg"
        );
    }

    @Test
    void shouldClearAvatarUrl_whenAvatarUrlProvidedAsNull() {
        User existing = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(
            Optional.of(existing)
        );
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
            null,
            false,
            null,
            false,
            null,
            true
        );

        useCase.updateProfile(USER_ID, command);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.fullName()).isEqualTo("Alice Smith");
        assertThat(saved.phoneNumber()).isEqualTo("+84912345678");
        assertThat(saved.avatarUrl()).isNull();
    }

    @Test
    void shouldUpdateAllThreeFields_whenAllProvided() {
        User existing = createSampleUser();
        when(userRepository.findById(USER_ID)).thenReturn(
            Optional.of(existing)
        );
        when(userRepository.save(any(User.class))).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
            "New Name",
            true,
            "+84987654321",
            true,
            "https://cdn.unihub.vn/new.jpg",
            true
        );

        useCase.updateProfile(USER_ID, command);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.fullName()).isEqualTo("New Name");
        assertThat(saved.phoneNumber()).isEqualTo("+84987654321");
        assertThat(saved.avatarUrl()).isEqualTo(
            "https://cdn.unihub.vn/new.jpg"
        );
    }

    @Test
    void shouldThrowValidationException_whenNoFieldsProvided() {
        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
            null,
            false,
            null,
            false,
            null,
            false
        );

        assertThatThrownBy(() -> useCase.updateProfile(USER_ID, command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "At least one field must be provided for update."
            );
    }

    @Test
    void shouldThrowUnauthorizedException_whenUserNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        UpdateUserProfileCommand command = new UpdateUserProfileCommand(
            "New Name",
            true,
            null,
            false,
            null,
            false
        );

        assertThatThrownBy(() -> useCase.updateProfile(USER_ID, command))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("User not found");
    }
}
