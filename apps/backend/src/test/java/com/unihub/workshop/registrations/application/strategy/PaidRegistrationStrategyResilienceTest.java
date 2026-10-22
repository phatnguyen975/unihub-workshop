package com.unihub.workshop.registrations.application.strategy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.CreatePaymentIntentCommand;
import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.usecases.PendingPaidRegistrationPersistenceService;
import com.unihub.workshop.registrations.application.usecases.RegisterWorkshopUseCaseImpl;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.idempotency.ClaimResult;
import com.unihub.workshop.shared.idempotency.IdempotencyPort;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaidRegistrationStrategyResilienceTest {

    private IamApi iamApi;
    private WorkshopApi workshopApi;
    private RegistrationRepository registrationRepository;
    private IdempotencyPort idempotencyPort;
    private CapacityPort capacityPort;
    private RegistrationPaymentPort registrationPaymentPort;
    private PendingPaidRegistrationPersistenceService persistenceService;

    private PaidRegistrationStrategy paidRegistrationStrategy;
    private RegisterWorkshopUseCaseImpl useCase;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final UUID idempotencyKeyRecordId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        iamApi = mock(IamApi.class);
        workshopApi = mock(WorkshopApi.class);
        registrationRepository = mock(RegistrationRepository.class);
        idempotencyPort = mock(IdempotencyPort.class);
        capacityPort = mock(CapacityPort.class);
        registrationPaymentPort = mock(RegistrationPaymentPort.class);
        persistenceService = mock(
            PendingPaidRegistrationPersistenceService.class
        );

        paidRegistrationStrategy = new PaidRegistrationStrategy(
            registrationPaymentPort,
            persistenceService,
            30L
        );

        useCase = new RegisterWorkshopUseCaseImpl(
            iamApi,
            workshopApi,
            registrationRepository,
            idempotencyPort,
            capacityPort,
            List.of(paidRegistrationStrategy)
        );

        // Standard setup for successful prerequisites
        StudentProfileDto student = new StudentProfileDto(
            studentUserId,
            UUID.randomUUID(),
            "Nguyen Van A",
            "student@uni.edu",
            "23123456"
        );
        when(iamApi.getStudentProfile(studentUserId)).thenReturn(
            Optional.of(student)
        );

        WorkshopRegistrationDetailsDto workshop =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                UUID.randomUUID(),
                "Cloud Native Java",
                "cloud-native-java",
                "PUBLISHED",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                new BigDecimal("50.00"),
                100,
                "Auditorium 1"
            );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        when(
            registrationRepository.existsActiveRegistration(
                studentUserId,
                workshopId
            )
        ).thenReturn(false);
        when(
            registrationRepository.hasOverlappingActiveRegistration(
                eq(studentUserId),
                any(Instant.class),
                any(Instant.class)
            )
        ).thenReturn(false);

        when(
            idempotencyPort.claim(
                eq(studentUserId),
                eq(idempotencyKey),
                eq("POST"),
                eq("/api/v1/registrations"),
                any(String.class),
                eq(Registration.class)
            )
        ).thenReturn(new ClaimResult.Acquired<>(idempotencyKeyRecordId));

        when(
            capacityPort.tryAcquireCapacity(eq(workshopId), eq(100), any())
        ).thenReturn(true);
    }

    @Test
    @DisplayName(
        "FR-14, BR-13: When Stripe call fails with ServiceUnavailableException, capacity is compensated and no DB record is saved"
    )
    void register_WhenStripeServiceUnavailable_CompensatesCapacityAndFailsIdempotency() {
        when(
            registrationPaymentPort.createPaymentIntent(
                any(CreatePaymentIntentCommand.class)
            )
        ).thenThrow(
            new ServiceUnavailableException(
                "Payment gateway service unavailable",
                30
            )
        );

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKey)
        )
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("Payment gateway service unavailable");

        verify(capacityPort).releaseCapacity(workshopId);
        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @Test
    @DisplayName(
        "FR-15, BR-13: When CircuitBreaker is OPEN, throws CallNotPermittedException, compensates capacity, and saves no DB record"
    )
    void register_WhenCircuitBreakerOpen_CompensatesCapacityAndFailsIdempotency() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("stripe-payment");
        cb.transitionToOpenState();
        CallNotPermittedException openException =
            CallNotPermittedException.createCallNotPermittedException(cb);

        when(
            registrationPaymentPort.createPaymentIntent(
                any(CreatePaymentIntentCommand.class)
            )
        ).thenThrow(openException);

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKey)
        ).isInstanceOf(CallNotPermittedException.class);

        verify(capacityPort).releaseCapacity(workshopId);
        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @Test
    @DisplayName(
        "When database persistence fails, compensates Redis capacity and marks idempotency failed"
    )
    void register_WhenDatabaseSaveFails_CompensatesCapacityAndFailsIdempotency() {
        when(
            registrationPaymentPort.createPaymentIntent(
                any(CreatePaymentIntentCommand.class)
            )
        ).thenReturn(
            new com.unihub.workshop.registrations.api.dto.PaymentIntentResult(
                "pi_999",
                "pi_999_secret_xyz",
                "requires_payment_method"
            )
        );

        when(
            persistenceService.persist(any(Registration.class), any())
        ).thenThrow(new RuntimeException("Database connection terminated"));

        assertThatThrownBy(() ->
            useCase.register(studentUserId, workshopId, idempotencyKey)
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Database connection terminated");

        verify(capacityPort).releaseCapacity(workshopId);
        verify(idempotencyPort).fail(studentUserId, idempotencyKey);
    }
}
