package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.strategy.RegistrationStrategy;
import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.idempotency.ClaimResult;
import com.unihub.workshop.shared.idempotency.IdempotencyConflictException;
import com.unihub.workshop.shared.idempotency.IdempotencyPort;
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
    private IdempotencyPort idempotencyPort;
    private CapacityPort capacityPort;
    private RegisterWorkshopUseCaseImpl useCase;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID studentRecordId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final UUID idempotencyKeyRecordId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        iamApi = mock(IamApi.class);
        workshopApi = mock(WorkshopApi.class);
        registrationRepository = mock(RegistrationRepository.class);
        strategy = mock(RegistrationStrategy.class);
        idempotencyPort = mock(IdempotencyPort.class);
        capacityPort = mock(CapacityPort.class);

        when(
            idempotencyPort.claim(
                eq(studentUserId),
                eq(idempotencyKey),
                any(),
                any(),
                any(),
                eq(Registration.class)
            )
        ).thenReturn(new ClaimResult.Acquired<>(idempotencyKeyRecordId));

        when(
            capacityPort.tryAcquireCapacity(eq(workshopId), anyInt(), any())
        ).thenReturn(true);

        useCase = new RegisterWorkshopUseCaseImpl(
            iamApi,
            workshopApi,
            registrationRepository,
            idempotencyPort,
            capacityPort,
            List.of(strategy)
        );
    }

    private StudentProfileDto validStudent() {
        return new StudentProfileDto(
            studentUserId,
            studentRecordId,
            "Alice Smith",
            "alice@uni.edu",
            "23123456"
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
            useCase.register(studentUserId, workshopId, idempotencyKey)
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
            "bob@uni.edu",
            null
        );
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(unlinked)
        );

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKey)
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
            useCase.register(studentUserId, workshopId, idempotencyKey)
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
            useCase.register(studentUserId, workshopId, idempotencyKey)
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
            useCase.register(studentUserId, workshopId, idempotencyKey)
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
            useCase.register(studentUserId, workshopId, idempotencyKey)
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
            useCase.register(studentUserId, workshopId, idempotencyKey)
        )
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already registered");
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
            useCase.register(studentUserId, workshopId, idempotencyKey)
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
            .idempotencyKeyId(idempotencyKeyRecordId)
            .qrTicket(
                new QrTicket("{\"payload\":\"test\"}", "sig123", Instant.now())
            )
            .build();

        when(
            strategy.register(student, workshop, idempotencyKeyRecordId)
        ).thenReturn(expected);

        Registration actual = useCase.register(
            studentUserId,
            workshopId,
            idempotencyKey
        );

        assertThat(actual).isNotNull();
        assertThat(actual.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(actual.qrTicket()).isNotNull();
        verify(strategy).register(student, workshop, idempotencyKeyRecordId);
        verify(idempotencyPort).complete(
            eq(studentUserId),
            eq(idempotencyKeyRecordId),
            eq(idempotencyKey),
            any(),
            eq(201),
            eq(expected)
        );
    }

    @Test
    @DisplayName(
        "FR-05, BR-04: Idempotent replay returns cached registration without invoking strategy or capacity"
    )
    void register_IdempotencyReplayed_ReturnsCachedRegistrationWithoutCallingStrategyOrCapacity() {
        Registration cached = Registration.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(idempotencyKeyRecordId)
            .build();

        when(
            idempotencyPort.claim(
                eq(studentUserId),
                eq(idempotencyKey),
                any(),
                any(),
                any(),
                eq(Registration.class)
            )
        ).thenReturn(new ClaimResult.Replayed<>(cached));

        Registration actual = useCase.register(
            studentUserId,
            workshopId,
            idempotencyKey
        );

        assertThat(actual).isSameAs(cached);
        verify(capacityPort, never()).tryAcquireCapacity(
            any(),
            anyInt(),
            any()
        );
        verify(strategy, never()).register(any(), any(), any());
    }

    @Test
    @DisplayName(
        "FR-11, BR-08: Throws ConflictException when capacity is exhausted and releases idempotency lock"
    )
    void register_CapacityExhausted_ThrowsConflictExceptionAndFailsIdempotency() {
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
                any(),
                any(),
                any()
            )
        ).thenReturn(false);

        when(
            capacityPort.tryAcquireCapacity(eq(workshopId), eq(100), any())
        ).thenReturn(false);

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKey)
        )
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("No seats remaining");

        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
        verify(strategy, never()).register(any(), any(), any());
    }

    @Test
    @DisplayName(
        "FR-04, BR-03: Throws IdempotencyConflictException directly when request is in-flight"
    )
    void register_InFlightIdempotencyConflict_ThrowsIdempotencyConflictException() {
        when(
            idempotencyPort.claim(
                eq(studentUserId),
                eq(idempotencyKey),
                any(),
                any(),
                any(),
                eq(Registration.class)
            )
        ).thenThrow(new IdempotencyConflictException("In flight", 2));

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKey)
        )
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessageContaining("In flight");

        verify(capacityPort, never()).tryAcquireCapacity(
            any(),
            anyInt(),
            any()
        );
    }

    @Test
    @DisplayName(
        "Strategy failure triggers compensating capacity release and marks idempotency failed"
    )
    void register_StrategyFailure_RollsBackCapacityAndFailsIdempotency() {
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
                any(),
                any(),
                any()
            )
        ).thenReturn(false);

        when(
            strategy.register(student, workshop, idempotencyKeyRecordId)
        ).thenThrow(
            new RuntimeException("Database error during registration insert")
        );

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKey)
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Database error");

        verify(capacityPort).releaseCapacity(workshopId);
        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
    }
}
