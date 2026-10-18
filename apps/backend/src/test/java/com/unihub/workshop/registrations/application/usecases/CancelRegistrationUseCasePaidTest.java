package com.unihub.workshop.registrations.application.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.RefundPaymentCommand;
import com.unihub.workshop.registrations.api.dto.RefundPaymentResult;
import com.unihub.workshop.registrations.application.ports.in.CancelRegistrationCommand;
import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.CancelledRegistrationResult;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.events.RegistrationCanceledEvent;
import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.idempotency.ClaimResult;
import com.unihub.workshop.shared.idempotency.IdempotencyPort;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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

class CancelRegistrationUseCasePaidTest {

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
    private final UUID idempotencyKeyRecordId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();
    private final BigDecimal price = new BigDecimal("50.00");
    private final String currency = "USD";

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

    private Registration createConfirmedPaidRegistration() {
        return Registration.builder()
            .id(registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(price)
            .currency(currency)
            .createdAt(Instant.now().minusSeconds(3600))
            .build();
    }

    private WorkshopRegistrationDetailsDto createPublishedWorkshop() {
        return new WorkshopRegistrationDetailsDto(
            workshopId,
            organizationId,
            "Advanced Cloud Architecture",
            "advanced-cloud-architecture",
            "PUBLISHED",
            Instant.now().plusSeconds(7200),
            Instant.now().plusSeconds(14400),
            price,
            50,
            "Room 301"
        );
    }

    private StudentProfileDto createStudentProfile() {
        return new StudentProfileDto(
            studentUserId,
            UUID.randomUUID(),
            "John Doe",
            "john.doe@university.edu"
        );
    }

    @Test
    @DisplayName(
        "cancel: successfully refunds and cancels paid registration for student owner (FR-31, FR-35, FR-36, FR-37, FR-38, FR-39)"
    )
    void cancel_WhenPaidRegistrationStudentOwner_IssuesRefundAndCancelsSuccessfully() {
        Registration reg = createConfirmedPaidRegistration();
        WorkshopRegistrationDetailsDto workshop = createPublishedWorkshop();
        StudentProfileDto student = createStudentProfile();

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
            registrationPaymentPort.hasCompletedPayment(registrationId)
        ).thenReturn(true);

        RefundPaymentResult refundResult = new RefundPaymentResult(
            "re_stripe_123",
            "succeeded",
            price,
            currency
        );
        when(
            registrationPaymentPort.refundPayment(
                any(RefundPaymentCommand.class)
            )
        ).thenReturn(refundResult);

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

        assertNotNull(result);
        assertEquals(
            RegistrationStatus.CANCELED,
            result.registration().status()
        );
        assertEquals(price, result.refundAmount());
        assertEquals(currency, result.refundCurrency());
        assertNull(result.registration().qrTicket());

        ArgumentCaptor<RefundPaymentCommand> refundCaptor =
            ArgumentCaptor.forClass(RefundPaymentCommand.class);
        verify(registrationPaymentPort).refundPayment(refundCaptor.capture());
        RefundPaymentCommand capturedRefund = refundCaptor.getValue();
        assertEquals(registrationId, capturedRefund.registrationId());
        assertEquals(price, capturedRefund.amount());
        assertEquals(currency, capturedRefund.currency());
        assertEquals(
            "refund-reg-" + registrationId,
            capturedRefund.idempotencyKey()
        );
        assertEquals("Schedule conflict", capturedRefund.reason());

        verify(registrationPaymentPort).markPaymentRefunded(
            registrationId,
            price,
            currency
        );

        ArgumentCaptor<RegistrationCanceledEvent> eventCaptor =
            ArgumentCaptor.forClass(RegistrationCanceledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        RegistrationCanceledEvent event = eventCaptor.getValue();
        assertEquals(registrationId, event.registrationId());
        assertEquals(workshopId, event.workshopId());
        assertEquals("Advanced Cloud Architecture", event.workshopTitle());
        assertEquals(studentUserId, event.studentId());
        assertEquals("john.doe@university.edu", event.studentEmail());
        assertEquals("John Doe", event.studentName());
        assertEquals("STUDENT", event.canceledByRole());
        assertEquals("Schedule conflict", event.reason());
        assertEquals(price, event.refundAmount());
        assertEquals(currency, event.currency());

        verify(capacityPort).releaseCapacity(workshopId);
        verify(realtimeEventPublisher).publishToWorkshop(eq(workshopId), any());
        verify(idempotencyPort).complete(
            eq(studentUserId),
            eq(idempotencyKeyRecordId),
            eq(idempotencyKey),
            any(),
            eq(200),
            eq(result)
        );
    }

    @Test
    @DisplayName(
        "cancel: successfully refunds and cancels paid registration for organizer of same organization (FR-18, FR-31)"
    )
    void cancel_WhenPaidRegistrationOrganizerOwner_IssuesRefundAndCancelsSuccessfully() {
        Registration reg = createConfirmedPaidRegistration();
        WorkshopRegistrationDetailsDto workshop = createPublishedWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.empty()
        );
        when(
            registrationPaymentPort.hasCompletedPayment(registrationId)
        ).thenReturn(true);

        RefundPaymentResult refundResult = new RefundPaymentResult(
            "re_stripe_456",
            "succeeded",
            price,
            currency
        );
        when(
            registrationPaymentPort.refundPayment(
                any(RefundPaymentCommand.class)
            )
        ).thenReturn(refundResult);

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
            null
        );

        CancelledRegistrationResult result = useCase.cancel(command);

        assertNotNull(result);
        assertEquals(
            RegistrationStatus.CANCELED,
            result.registration().status()
        );
        assertEquals(price, result.refundAmount());
        assertEquals(currency, result.refundCurrency());

        verify(registrationPaymentPort).markPaymentRefunded(
            registrationId,
            price,
            currency
        );

        ArgumentCaptor<RegistrationCanceledEvent> eventCaptor =
            ArgumentCaptor.forClass(RegistrationCanceledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        RegistrationCanceledEvent event = eventCaptor.getValue();
        assertEquals("ORGANIZER", event.canceledByRole());
        assertEquals(price, event.refundAmount());
        assertEquals(currency, event.currency());
    }

    @Test
    @DisplayName(
        "cancel: throws InternalServerException when paid registration has no COMPLETED payment record (FR-34, Scenario 29)"
    )
    void cancel_WhenPaidRegistrationMissingOrNonCompletedPayment_ThrowsInternalServerException() {
        Registration reg = createConfirmedPaidRegistration();
        WorkshopRegistrationDetailsDto workshop = createPublishedWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationPaymentPort.hasCompletedPayment(registrationId)
        ).thenReturn(false);

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        InternalServerException ex = assertThrows(
            InternalServerException.class,
            () -> useCase.cancel(command)
        );

        assertTrue(
            ex
                .getMessage()
                .contains(
                    "Paid registration has no payment record in COMPLETED status"
                )
        );

        verify(registrationPaymentPort, never()).refundPayment(any());
        verify(registrationRepository, never()).cancelRegistration(
            any(),
            any(),
            any()
        );
        verify(registrationPaymentPort, never()).markPaymentRefunded(
            any(),
            any(),
            any()
        );
        verify(eventPublisher, never()).publishEvent(any());
        verify(capacityPort, never()).releaseCapacity(any());
        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
    }

    @Test
    @DisplayName(
        "cancel: throws ServiceUnavailableException when Stripe refund fails, leaving DB and counter untouched (FR-33, Scenario 20)"
    )
    void cancel_WhenStripeRefundThrowsServiceUnavailable_LeavesDatabaseUntouched() {
        Registration reg = createConfirmedPaidRegistration();
        WorkshopRegistrationDetailsDto workshop = createPublishedWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationPaymentPort.hasCompletedPayment(registrationId)
        ).thenReturn(true);

        when(
            registrationPaymentPort.refundPayment(
                any(RefundPaymentCommand.class)
            )
        ).thenThrow(
            new ServiceUnavailableException(
                "Payment gateway service unavailable. Please retry later.",
                30
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

        ServiceUnavailableException ex = assertThrows(
            ServiceUnavailableException.class,
            () -> useCase.cancel(command)
        );

        assertEquals(30, ex.getRetryAfterSeconds());

        verify(registrationRepository, never()).cancelRegistration(
            any(),
            any(),
            any()
        );
        verify(registrationPaymentPort, never()).markPaymentRefunded(
            any(),
            any(),
            any()
        );
        verify(eventPublisher, never()).publishEvent(any());
        verify(capacityPort, never()).releaseCapacity(any());
        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
    }

    @Test
    @DisplayName(
        "cancel: throws CallNotPermittedException when circuit breaker is open, leaving DB untouched (FR-33)"
    )
    void cancel_WhenCircuitBreakerOpen_LeavesDatabaseUntouched() {
        Registration reg = createConfirmedPaidRegistration();
        WorkshopRegistrationDetailsDto workshop = createPublishedWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationPaymentPort.hasCompletedPayment(registrationId)
        ).thenReturn(true);

        CircuitBreaker cb = CircuitBreaker.ofDefaults("test-cb");
        cb.transitionToOpenState();
        CallNotPermittedException cbException =
            CallNotPermittedException.createCallNotPermittedException(cb);

        when(
            registrationPaymentPort.refundPayment(
                any(RefundPaymentCommand.class)
            )
        ).thenThrow(cbException);

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        assertThrows(CallNotPermittedException.class, () ->
            useCase.cancel(command)
        );

        verify(registrationRepository, never()).cancelRegistration(
            any(),
            any(),
            any()
        );
        verify(registrationPaymentPort, never()).markPaymentRefunded(
            any(),
            any(),
            any()
        );
        verify(eventPublisher, never()).publishEvent(any());
        verify(capacityPort, never()).releaseCapacity(any());
        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
    }

    @Test
    @DisplayName(
        "cancel: throws ValidationException when concurrent cancellation wins DB transition (FR-40, Scenario 23)"
    )
    void cancel_WhenConcurrentCancellationOccurs_ThrowsValidationException() {
        Registration reg = createConfirmedPaidRegistration();
        WorkshopRegistrationDetailsDto workshop = createPublishedWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationPaymentPort.hasCompletedPayment(registrationId)
        ).thenReturn(true);

        RefundPaymentResult refundResult = new RefundPaymentResult(
            "re_stripe_789",
            "succeeded",
            price,
            currency
        );
        when(
            registrationPaymentPort.refundPayment(
                any(RefundPaymentCommand.class)
            )
        ).thenReturn(refundResult);

        when(
            registrationRepository.cancelRegistration(
                eq(registrationId),
                eq(studentUserId),
                any(Instant.class)
            )
        ).thenReturn(false);

        Registration alreadyCanceled = reg
            .toBuilder()
            .status(RegistrationStatus.CANCELED)
            .build();
        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(alreadyCanceled)
        );

        CancelRegistrationCommand command = new CancelRegistrationCommand(
            registrationId,
            studentUserId,
            "STUDENT",
            null,
            idempotencyKey,
            null
        );

        ValidationException ex = assertThrows(ValidationException.class, () ->
            useCase.cancel(command)
        );

        assertEquals("Registration is already canceled", ex.getMessage());

        verify(registrationPaymentPort, never()).markPaymentRefunded(
            any(),
            any(),
            any()
        );
        verify(eventPublisher, never()).publishEvent(any());
        verify(capacityPort, never()).releaseCapacity(any());
        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
    }

    @Test
    @DisplayName(
        "cancel: Redis capacity failure after commit does not fail cancellation (BR-06)"
    )
    void cancel_WhenRedisCapacityFails_DoesNotFailCancellation() {
        Registration reg = createConfirmedPaidRegistration();
        WorkshopRegistrationDetailsDto workshop = createPublishedWorkshop();

        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationPaymentPort.hasCompletedPayment(registrationId)
        ).thenReturn(true);

        RefundPaymentResult refundResult = new RefundPaymentResult(
            "re_stripe_999",
            "succeeded",
            price,
            currency
        );
        when(
            registrationPaymentPort.refundPayment(
                any(RefundPaymentCommand.class)
            )
        ).thenReturn(refundResult);

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

        assertNotNull(result);
        assertEquals(
            RegistrationStatus.CANCELED,
            result.registration().status()
        );
        assertEquals(price, result.refundAmount());
    }
}
