package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.strategy.RegistrationStrategy;
import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegisterWorkshopUseCaseImplTest {

    private IamApi iamApi;
    private WorkshopApi workshopApi;
    private RegistrationRepository registrationRepository;
    private RegistrationStrategy strategy;
    private RegisterWorkshopUseCaseImpl useCase;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID studentRecordId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final UUID idempotencyKeyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        iamApi = mock(IamApi.class);
        workshopApi = mock(WorkshopApi.class);
        registrationRepository = mock(RegistrationRepository.class);
        strategy = mock(RegistrationStrategy.class);

        useCase = new RegisterWorkshopUseCaseImpl(
            iamApi,
            workshopApi,
            registrationRepository,
            List.of(strategy)
        );
    }

    private StudentProfileDto validStudent() {
        return new StudentProfileDto(
            studentUserId,
            studentRecordId,
            "Alice Smith",
            "alice@uni.edu"
        );
    }

    private WorkshopRegistrationDetailsDto validFreeWorkshop() {
        return new WorkshopRegistrationDetailsDto(
            workshopId,
            UUID.randomUUID(),
            "Cloud Native Java",
            "cloud-native-java",
            "PUBLISHED",
            Instant.now().plusSeconds(3600),
            Instant.now().plusSeconds(7200),
            BigDecimal.ZERO,
            100,
            "Auditorium 1"
        );
    }

    @Test
    @DisplayName(
        "FR-02: Throws ForbiddenException when student profile is not found in IAM"
    )
    void register_StudentNotFound_ThrowsForbidden() {
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKeyId)
        )
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("not linked");
    }

    @Test
    @DisplayName(
        "FR-02: Throws ForbiddenException when studentRecordId is null (unlinked)"
    )
    void register_UnlinkedStudentRecord_ThrowsForbidden() {
        StudentProfileDto unlinked = new StudentProfileDto(
            studentUserId,
            null,
            "Bob",
            "bob@uni.edu"
        );
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(unlinked)
        );

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKeyId)
        )
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("not linked");
    }

    @Test
    @DisplayName("FR-06: Throws NotFoundException when workshop does not exist")
    void register_WorkshopNotFound_ThrowsNotFound() {
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(validStudent())
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKeyId)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining(workshopId.toString());
    }

    @Test
    @DisplayName(
        "FR-07: Throws ValidationException when workshop status is not PUBLISHED"
    )
    void register_WorkshopNotPublished_ThrowsValidationException() {
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(validStudent())
        );

        WorkshopRegistrationDetailsDto draft =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                UUID.randomUUID(),
                "Draft Workshop",
                "draft-workshop",
                "DRAFT",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                BigDecimal.ZERO,
                100,
                "Room 1"
            );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(draft)
        );

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKeyId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("not published");
    }

    @Test
    @DisplayName(
        "FR-07: Throws ValidationException when workshop has already started"
    )
    void register_WorkshopAlreadyStarted_ThrowsValidationException() {
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(validStudent())
        );

        WorkshopRegistrationDetailsDto past =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                UUID.randomUUID(),
                "Past Workshop",
                "past-workshop",
                "PUBLISHED",
                Instant.now().minusSeconds(100),
                Instant.now().plusSeconds(3600),
                BigDecimal.ZERO,
                100,
                "Room 1"
            );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(past)
        );

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKeyId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("already started");
    }

    @Test
    @DisplayName(
        "FR-09: Throws ValidationException when price > 0 and no paid strategy is registered"
    )
    void register_PaidWorkshopInFreeFlow_ThrowsValidationException() {
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(validStudent())
        );

        WorkshopRegistrationDetailsDto paid =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                UUID.randomUUID(),
                "Paid Workshop",
                "paid-workshop",
                "PUBLISHED",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                new BigDecimal("50.00"),
                100,
                "Room 1"
            );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(paid)
        );
        when(strategy.supports(paid)).thenReturn(false);

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKeyId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("checkout flow");
    }

    @Test
    @DisplayName(
        "FR-10, BR-06: Throws ConflictException when student already holds active registration for same workshop"
    )
    void register_DuplicateActiveRegistration_ThrowsConflict() {
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(validStudent())
        );
        WorkshopRegistrationDetailsDto workshop = validFreeWorkshop();
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(strategy.supports(workshop)).thenReturn(true);
        when(
            registrationRepository.existsActiveRegistration(
                studentUserId,
                workshopId
            )
        ).thenReturn(true);

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKeyId)
        )
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName(
        "FR-10, BR-07: Throws ValidationException when schedule conflicts with another active registration"
    )
    void register_ScheduleConflict_ThrowsValidationException() {
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(validStudent())
        );
        WorkshopRegistrationDetailsDto workshop = validFreeWorkshop();
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(strategy.supports(workshop)).thenReturn(true);
        when(
            registrationRepository.existsActiveRegistration(
                studentUserId,
                workshopId
            )
        ).thenReturn(false);
        when(
            registrationRepository.hasOverlappingActiveRegistration(
                studentUserId,
                workshop.startsAt(),
                workshop.endsAt()
            )
        ).thenReturn(true);

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKeyId)
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Schedule conflict");
    }

    @Test
    @DisplayName(
        "Scenario 1: Successfully executes free registration and returns confirmed registration"
    )
    void register_ValidFreeRegistration_ReturnsConfirmedRegistration() {
        StudentProfileDto student = validStudent();
        WorkshopRegistrationDetailsDto workshop = validFreeWorkshop();

        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(student)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(strategy.supports(workshop)).thenReturn(true);
        when(
            registrationRepository.existsActiveRegistration(
                studentUserId,
                workshopId
            )
        ).thenReturn(false);
        when(
            registrationRepository.hasOverlappingActiveRegistration(
                studentUserId,
                workshop.startsAt(),
                workshop.endsAt()
            )
        ).thenReturn(false);

        Registration expected = Registration.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(idempotencyKeyId)
            .qrTicket(
                new QrTicket("{\"payload\":\"test\"}", "sig123", Instant.now())
            )
            .build();

        when(strategy.register(student, workshop, idempotencyKeyId)).thenReturn(
            expected
        );

        Registration actual = useCase.register(
            studentUserId,
            workshopId,
            idempotencyKeyId
        );

        assertThat(actual).isNotNull();
        assertThat(actual.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(actual.qrTicket()).isNotNull();
        verify(strategy).register(student, workshop, idempotencyKeyId);
    }
}
