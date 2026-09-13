package com.unihub.workshop.iam.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.application.ports.in.ProvisionUserCommand;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.events.UserProvisionedEvent;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.seed.PasswordGenerator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ProvisionUserUseCaseImplTest {

    private UserRepository userRepository;
    private PasswordGenerator passwordGenerator;
    private ApplicationEventPublisher eventPublisher;
    private ProvisionUserUseCaseImpl useCase;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordGenerator = mock(PasswordGenerator.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new ProvisionUserUseCaseImpl(
            userRepository,
            passwordGenerator,
            eventPublisher
        );
    }

    @Test
    void shouldThrowBadRequest_whenCommandIsNull() {
        assertThatThrownBy(() -> useCase.execute(null))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Command must not be null");
    }

    @Test
    void shouldThrowBadRequest_whenOrganizationIdIsNull() {
        ProvisionUserCommand command = new ProvisionUserCommand(
            null,
            CALLER_ID,
            "test@test.org",
            "Test User",
            null,
            UserRole.STAFF
        );
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("organizationId must not be null");
    }

    @Test
    void shouldThrowBadRequest_whenCallerIdIsNull() {
        ProvisionUserCommand command = new ProvisionUserCommand(
            ORG_ID,
            null,
            "test@test.org",
            "Test User",
            null,
            UserRole.STAFF
        );
        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("callerId must not be null");
    }

    @Test
    void shouldThrowValidationException_whenRoleIsStudent() {
        ProvisionUserCommand command = new ProvisionUserCommand(
            ORG_ID,
            CALLER_ID,
            "student@test.org",
            "Student User",
            null,
            UserRole.STUDENT
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> {
                ValidationException ve = (ValidationException) e;
                assertThat(ve.getType()).isEqualTo(
                    "urn:unihub:error:student-provisioning-forbidden"
                );
            });

        verifyNoInteractions(passwordGenerator);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldThrowConflictException_whenEmailAlreadyExists() {
        ProvisionUserCommand command = new ProvisionUserCommand(
            ORG_ID,
            CALLER_ID,
            "existing@test.org",
            "Staff User",
            "+84901234567",
            UserRole.STAFF
        );

        when(userRepository.existsByEmail("existing@test.org")).thenReturn(
            true
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessage("An account with this email already exists.");

        verifyNoInteractions(passwordGenerator);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldProvisionStaffMember_whenValidCommand() {
        ProvisionUserCommand command = new ProvisionUserCommand(
            ORG_ID,
            CALLER_ID,
            "  New.Staff@TechClub.org  ",
            "  Alice Nguyen  ",
            " +84901234567 ",
            UserRole.STAFF
        );

        when(userRepository.existsByEmail("new.staff@techclub.org")).thenReturn(
            false
        );
        when(passwordGenerator.generate()).thenReturn(
            new PasswordGenerator.GeneratedPassword(
                "plainTempPass123",
                "argon2hash456"
            )
        );

        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            return new User(
                generatedId,
                userToSave.organizationId(),
                userToSave.role(),
                userToSave.email(),
                userToSave.phoneNumber(),
                userToSave.passwordHash(),
                userToSave.fullName(),
                null,
                userToSave.isActive(),
                userToSave.isTemporaryPassword(),
                null,
                null,
                Instant.now(),
                Instant.now(),
                userToSave.createdBy(),
                userToSave.updatedBy()
            );
        });

        User result = useCase.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(generatedId);
        assertThat(result.organizationId()).isEqualTo(ORG_ID);
        assertThat(result.email()).isEqualTo("new.staff@techclub.org");
        assertThat(result.fullName()).isEqualTo("Alice Nguyen");
        assertThat(result.phoneNumber()).isEqualTo("+84901234567");
        assertThat(result.role()).isEqualTo(UserRole.STAFF);
        assertThat(result.isActive()).isTrue();
        assertThat(result.isTemporaryPassword()).isTrue();
        assertThat(result.passwordHash()).isEqualTo("argon2hash456");
        assertThat(result.createdBy()).isEqualTo(CALLER_ID);

        // Verify outbox event publication
        ArgumentCaptor<UserProvisionedEvent> eventCaptor =
            ArgumentCaptor.forClass(UserProvisionedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        UserProvisionedEvent event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(generatedId);
        assertThat(event.organizationId()).isEqualTo(ORG_ID);
        assertThat(event.email()).isEqualTo("new.staff@techclub.org");
        assertThat(event.fullName()).isEqualTo("Alice Nguyen");
        assertThat(event.role()).isEqualTo("STAFF");
        assertThat(event.temporaryPassword()).isEqualTo("plainTempPass123");
    }

    @Test
    void shouldProvisionOrganizerMember_whenValidCommand() {
        ProvisionUserCommand command = new ProvisionUserCommand(
            ORG_ID,
            CALLER_ID,
            "org.lead@techclub.org",
            "Lead Organizer",
            null,
            UserRole.ORGANIZER
        );

        when(userRepository.existsByEmail("org.lead@techclub.org")).thenReturn(
            false
        );
        when(passwordGenerator.generate()).thenReturn(
            new PasswordGenerator.GeneratedPassword(
                "plainTempPass789",
                "argon2hash789"
            )
        );

        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            return new User(
                generatedId,
                userToSave.organizationId(),
                userToSave.role(),
                userToSave.email(),
                userToSave.phoneNumber(),
                userToSave.passwordHash(),
                userToSave.fullName(),
                null,
                userToSave.isActive(),
                userToSave.isTemporaryPassword(),
                null,
                null,
                Instant.now(),
                Instant.now(),
                userToSave.createdBy(),
                userToSave.updatedBy()
            );
        });

        User result = useCase.execute(command);

        assertThat(result.role()).isEqualTo(UserRole.ORGANIZER);
        verify(eventPublisher).publishEvent(any(UserProvisionedEvent.class));
    }
}
