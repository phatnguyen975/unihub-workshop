package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.application.ports.in.CancelRegistrationCommand;
import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.CancelledRegistrationResult;
import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.events.RegistrationCanceledEvent;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.idempotency.ClaimResult;
import com.unihub.workshop.shared.idempotency.IdempotencyPort;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class CancelRegistrationUseCaseFreeTest {

    private RegistrationRepository registrationRepository;
    private WorkshopApi workshopApi;
    private IamApi iamApi;
    private IdempotencyPort idempotencyPort;
    private CapacityPort capacityPort;
    private RealtimeEventPublisher realtimeEventPublisher;
    private ApplicationEventPublisher eventPublisher;
    private TransactionOperations transactionOperations;
    private RegistrationPaymentPort registrationPaymentPort;

    private CancelRegistrationUseCaseImpl useCase;

    private final UUID registrationId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final UUID studentUserId = UUID.randomUUID();
    private final UUID organizerUserId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID otherOrgId = UUID.randomUUID();
    private final UUID idempotencyKeyRecordId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        workshopApi = mock(WorkshopApi.class);
        iamApi = mock(IamApi.class);
        idempotencyPort = mock(IdempotencyPort.class);
        capacityPort = mock(CapacityPort.class);
        realtimeEventPublisher = mock(RealtimeEventPublisher.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        transactionOperations = mock(TransactionOperations.class);
        registrationPaymentPort = mock(RegistrationPaymentPort.class);

        when(transactionOperations.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        // Default acquired idempotency key claim
        ClaimResult.Acquired<CancelledRegistrationResult> acquired =
            new ClaimResult.Acquired<>(idempotencyKeyRecordId);
        when(
            idempotencyPort.claim(
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(CancelledRegistrationResult.class)
            )
        ).thenReturn(acquired);

        useCase = new CancelRegistrationUseCaseImpl(
            registrationRepository,
            workshopApi,
            iamApi,
            idempotencyPort,
            capacityPort,
            realtimeEventPublisher,
            eventPublisher,
            transactionOperations,
            registrationPaymentPort
        );
    }

    private Registration buildConfirmedFreeRegistration() {
        Instant now = Instant.now();
        return Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .qrTicket(new QrTicket("payload", "sig", now))
            .createdAt(now.minusSeconds(3600))
            .updatedAt(now.minusSeconds(3600))
            .createdBy(studentUserId)
            .updatedBy(studentUserId)
            .build();
    }

    private WorkshopRegistrationDetailsDto buildPublishedFutureWorkshop() {
        return new WorkshopRegistrationDetailsDto(
            workshopId,
            organizationId,
            "Advanced Cloud Architecture",
            "advanced-cloud-architecture",
            "PUBLISHED",
            Instant.now().plusSeconds(86400),
            Instant.now().plusSeconds(90000),
            BigDecimal.ZERO,
            100,
            "Hall A"
        );
    }

    private StudentProfileDto buildStudentProfile() {
        return new StudentProfileDto(
            studentUserId,
            UUID.randomUUID(),
            "Alice Smith",
            "alice@example.com"
        );
    }

    @Test
    @DisplayName(
        "FR-17, FR-30, FR-35: Owning STUDENT cancels free registration successfully"
    )
    void cancel_StudentOwnRegistration_Succeeds() {
        Registration reg = buildConfirmedFreeRegistration();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();
        StudentProfileDto student = buildStudentProfile();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(student)
        );
        when(
            registrationRepository.cancelRegistration(
                eq(registrationId),
                eq(studentUserId),
                any(Instant.class)
            )
        ).thenReturn(true);

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            "Schedule conflict"
        );

        CancelledRegistrationResult result = useCase.cancel(command);

        assertThat(result).isNotNull();
        assertThat(result.registration().status()).isEqualTo(
            RegistrationStatus.CANCELED
        );
        assertThat(result.registration().updatedBy()).isEqualTo(studentUserId);
        assertThat(result.refundAmount()).isNull();
        assertThat(result.refundCurrency()).isNull();

        // Verify outbox integration event
        ArgumentCaptor<RegistrationCanceledEvent> eventCaptor =
            ArgumentCaptor.captor();
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        RegistrationCanceledEvent event = eventCaptor.getValue();
        assertThat(event.registrationId()).isEqualTo(registrationId);
        assertThat(event.studentEmail()).isEqualTo("alice@example.com");
        assertThat(event.workshopTitle()).isEqualTo(
            "Advanced Cloud Architecture"
        );
        assertThat(event.reason()).isEqualTo("Schedule conflict");
        assertThat(event.canceledByRole()).isEqualTo("STUDENT");
        assertThat(event.refundAmount()).isNull();

        // Verify post-commit side effects
        verify(capacityPort).releaseCapacity(workshopId);
        verify(realtimeEventPublisher).publishToWorkshop(
            eq(workshopId),
            any(RealtimeEvent.class)
        );
        verify(idempotencyPort).complete(
            eq(studentUserId),
            eq(idempotencyKeyRecordId),
            eq(idempotencyKey),
            anyString(),
            eq(200),
            eq(result)
        );
    }

    @Test
    @DisplayName(
        "FR-18, FR-35: ORGANIZER of owning organization cancels free registration successfully"
    )
    void cancel_OrganizerOwningOrg_Succeeds() {
        Registration reg = buildConfirmedFreeRegistration();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();
        StudentProfileDto student = buildStudentProfile();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(student)
        );
        when(
            registrationRepository.cancelRegistration(
                eq(registrationId),
                eq(organizerUserId),
                any(Instant.class)
            )
        ).thenReturn(true);

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            organizerUserId,
            "ORGANIZER",
            organizationId,
            idempotencyKey,
            "   Organizer requested cancellation   "
        );

        CancelledRegistrationResult result = useCase.cancel(command);

        assertThat(result.registration().status()).isEqualTo(
            RegistrationStatus.CANCELED
        );
        assertThat(result.registration().updatedBy()).isEqualTo(
            organizerUserId
        );

        ArgumentCaptor<RegistrationCanceledEvent> eventCaptor =
            ArgumentCaptor.captor();
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        RegistrationCanceledEvent event = eventCaptor.getValue();
        assertThat(event.reason()).isEqualTo(
            "Organizer requested cancellation"
        ); // trimmed
        assertThat(event.canceledByRole()).isEqualTo("ORGANIZER");

        verify(capacityPort).releaseCapacity(workshopId);
        verify(realtimeEventPublisher).publishToWorkshop(
            eq(workshopId),
            any(RealtimeEvent.class)
        );
    }

    @Test
    @DisplayName(
        "FR-28, Scenario 22: Idempotent replay returns cached response without repeating side effects"
    )
    void cancel_IdempotentReplay_ReturnsCachedResponse() {
        Registration reg = buildConfirmedFreeRegistration().cancel(
            studentUserId,
            Instant.now()
        );
        CancelledRegistrationResult cachedResult =
            new CancelledRegistrationResult(reg, null, null);
        ClaimResult.Replayed<CancelledRegistrationResult> replayed =
            new ClaimResult.Replayed<>(cachedResult);

        when(
            idempotencyPort.claim(
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(CancelledRegistrationResult.class)
            )
        ).thenReturn(replayed);

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        CancelledRegistrationResult result = useCase.cancel(command);

        assertThat(result).isSameAs(cachedResult);
        verify(registrationRepository, never()).findById(any());
        verify(capacityPort, never()).releaseCapacity(any());
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(realtimeEventPublisher);
    }

    @Test
    @DisplayName(
        "FR-20, Scenario 15: Throws NotFoundException when registration does not exist"
    )
    void cancel_NonExistentRegistration_ThrowsNotFound() {
        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.empty()
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Registration not found");

        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
    }

    @Test
    @DisplayName(
        "FR-17, FR-22: Non-owning STUDENT rejected with ForbiddenException before state validation"
    )
    void cancel_NonOwningStudent_ThrowsForbidden() {
        UUID otherStudentId = UUID.randomUUID();
        Registration reg = buildConfirmedFreeRegistration(); // owned by studentUserId
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            otherStudentId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("permission");

        verify(idempotencyPort).fail(otherStudentId, idempotencyKey);
        verify(registrationRepository, never()).cancelRegistration(
            any(),
            any(),
            any()
        );
    }

    @Test
    @DisplayName(
        "FR-18, FR-22: ORGANIZER from different organization rejected with ForbiddenException"
    )
    void cancel_OrganizerDifferentOrg_ThrowsForbidden() {
        Registration reg = buildConfirmedFreeRegistration();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop(); // orgId = organizationId

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            organizerUserId,
            "ORGANIZER",
            otherOrgId, // different org
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("permission");

        verify(idempotencyPort).fail(organizerUserId, idempotencyKey);
        verify(registrationRepository, never()).cancelRegistration(
            any(),
            any(),
            any()
        );
    }

    @Test
    @DisplayName(
        "FR-19: Unauthorized role (STAFF) rejected with ForbiddenException"
    )
    void cancel_UnauthorizedRole_ThrowsForbidden() {
        Registration reg = buildConfirmedFreeRegistration();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            UUID.randomUUID(),
            "STAFF",
            organizationId,
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("permission");
    }

    @Test
    @DisplayName(
        "FR-24, Scenario 16: Registration already CANCELED throws ValidationException"
    )
    void cancel_AlreadyCanceled_ThrowsValidationException() {
        Registration reg = buildConfirmedFreeRegistration()
            .toBuilder()
            .status(RegistrationStatus.CANCELED)
            .build();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Registration is already canceled");

        verify(registrationRepository, never()).cancelRegistration(
            any(),
            any(),
            any()
        );
    }

    @Test
    @DisplayName(
        "FR-24, Scenario 17: Registration in FAILED status throws ValidationException"
    )
    void cancel_FailedStatus_ThrowsValidationException() {
        Registration reg = buildConfirmedFreeRegistration()
            .toBuilder()
            .status(RegistrationStatus.FAILED)
            .build();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Cannot cancel a failed registration");
    }

    @Test
    @DisplayName(
        "FR-24: Registration in PENDING status throws ValidationException"
    )
    void cancel_PendingStatus_ThrowsValidationException() {
        Registration reg = buildConfirmedFreeRegistration()
            .toBuilder()
            .status(RegistrationStatus.PENDING)
            .build();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Only CONFIRMED registrations can be canceled"
            );
    }

    @Test
    @DisplayName(
        "FR-25: Workshop not in PUBLISHED status throws ValidationException"
    )
    void cancel_WorkshopNotPublished_ThrowsValidationException() {
        Registration reg = buildConfirmedFreeRegistration();
        WorkshopRegistrationDetailsDto workshop =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                organizationId,
                "Draft Workshop",
                "draft-workshop",
                "DRAFT", // not PUBLISHED
                Instant.now().plusSeconds(86400),
                Instant.now().plusSeconds(90000),
                BigDecimal.ZERO,
                100,
                "Hall A"
            );

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Workshop is not in a cancelable state");
    }

    @Test
    @DisplayName(
        "FR-26, Scenario 18: Workshop already started or ended throws ValidationException"
    )
    void cancel_WorkshopAlreadyStarted_ThrowsValidationException() {
        Registration reg = buildConfirmedFreeRegistration();
        WorkshopRegistrationDetailsDto workshop =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                organizationId,
                "Past Workshop",
                "past-workshop",
                "PUBLISHED",
                Instant.now().minusSeconds(60), // started 1 minute ago
                Instant.now().plusSeconds(3600),
                BigDecimal.ZERO,
                100,
                "Hall A"
            );

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Cannot cancel registration for a workshop that has already started or ended"
            );
    }

    @Test
    @DisplayName(
        "FR-23, Scenario 19: Reason exceeding 1000 characters throws ValidationException"
    )
    void cancel_ReasonExceeding1000Chars_ThrowsValidationException() {
        String longReason = "a".repeat(1001);

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            longReason
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Cancellation reason must not exceed 1000 characters"
            );

        verifyNoInteractions(registrationRepository);
    }

    @Test
    @DisplayName(
        "FR-23: Blank or whitespace-only reason treated as null in event"
    )
    void cancel_BlankReason_TreatedAsNull() {
        Registration reg = buildConfirmedFreeRegistration();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();
        StudentProfileDto student = buildStudentProfile();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(student)
        );
        when(
            registrationRepository.cancelRegistration(
                eq(registrationId),
                eq(studentUserId),
                any(Instant.class)
            )
        ).thenReturn(true);

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            "   \t\n   "
        );

        useCase.cancel(command);

        ArgumentCaptor<RegistrationCanceledEvent> eventCaptor =
            ArgumentCaptor.captor();
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().reason()).isNull();
    }

    @Test
    @DisplayName(
        "FR-40, Scenario 23: Concurrent cancellation lost race throws ValidationException"
    )
    void cancel_ConcurrentCancellationLostRace_ThrowsValidationException() {
        Registration reg = buildConfirmedFreeRegistration();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        // Atomic update returns false because concurrent request already flipped status
        when(
            registrationRepository.cancelRegistration(
                eq(registrationId),
                eq(studentUserId),
                any(Instant.class)
            )
        ).thenReturn(false);

        // Subsequent findById returns already CANCELED
        when(registrationRepository.findById(registrationId))
            .thenReturn(Optional.of(reg))
            .thenReturn(
                Optional.of(
                    reg.toBuilder().status(RegistrationStatus.CANCELED).build()
                )
            );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        assertThatThrownBy(() -> useCase.cancel(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Registration is already canceled");

        verify(capacityPort, never()).releaseCapacity(any());
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(realtimeEventPublisher);
    }

    @Test
    @DisplayName(
        "FR-37, Scenario 24: Redis capacity failure after commit does not fail request"
    )
    void cancel_RedisFailureAfterCommit_DoesNotFailRequest() {
        Registration reg = buildConfirmedFreeRegistration();
        WorkshopRegistrationDetailsDto workshop =
            buildPublishedFutureWorkshop();
        StudentProfileDto student = buildStudentProfile();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(student)
        );
        when(
            registrationRepository.cancelRegistration(
                eq(registrationId),
                eq(studentUserId),
                any(Instant.class)
            )
        ).thenReturn(true);

        doThrow(new RuntimeException("Redis connection error"))
            .when(capacityPort)
            .releaseCapacity(workshopId);

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        CancelledRegistrationResult result = useCase.cancel(command);

        assertThat(result).isNotNull();
        assertThat(result.registration().status()).isEqualTo(
            RegistrationStatus.CANCELED
        );
        verify(idempotencyPort).complete(
            eq(studentUserId),
            eq(idempotencyKeyRecordId),
            eq(idempotencyKey),
            anyString(),
            eq(200),
            eq(result)
        );
    }
}
