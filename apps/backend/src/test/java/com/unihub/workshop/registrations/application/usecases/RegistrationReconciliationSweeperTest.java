package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.FailPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.PaymentStatusResult;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import com.unihub.workshop.shared.idempotency.IdempotencyKeyJpaEntity;
import com.unihub.workshop.shared.idempotency.IdempotencyKeyJpaRepository;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class RegistrationReconciliationSweeperTest {

    private RegistrationRepository registrationRepository;
    private RegistrationPaymentPort registrationPaymentPort;
    private RegistrationApi registrationApi;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;
    private RealtimeEventPublisher realtimeEventPublisher;
    private TransactionOperations transactionOperations;
    private RegistrationReconciliationSweeper sweeper;

    private final UUID registrationId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final UUID studentUserId = UUID.randomUUID();
    private final UUID idempotencyKeyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        registrationPaymentPort = mock(RegistrationPaymentPort.class);
        registrationApi = mock(RegistrationApi.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        idempotencyKeyJpaRepository = mock(IdempotencyKeyJpaRepository.class);
        realtimeEventPublisher = mock(RealtimeEventPublisher.class);
        transactionOperations = mock(TransactionOperations.class);

        when(transactionOperations.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        // Default lock acquisition returns true
        when(
            valueOperations.setIfAbsent(
                eq("lock:sweeper:registration-reconciliation"),
                eq("1"),
                eq(Duration.ofSeconds(240))
            )
        ).thenReturn(true);

        sweeper = new RegistrationReconciliationSweeper(
            registrationRepository,
            registrationPaymentPort,
            registrationApi,
            redisTemplate,
            idempotencyKeyJpaRepository,
            realtimeEventPublisher,
            transactionOperations,
            100
        );
    }

    private Registration buildPendingRegistration(UUID id) {
        return Registration.builder()
            .id(id != null ? id : registrationId)
            .workshopId(workshopId)
            .studentUserId(studentUserId)
            .status(RegistrationStatus.PENDING)
            .priceAtRegistration(new BigDecimal("50.00"))
            .currency("USD")
            .idempotencyKeyId(idempotencyKeyId)
            .expiresAt(Instant.now().minusSeconds(60))
            .createdAt(Instant.now().minusSeconds(1800))
            .updatedAt(Instant.now().minusSeconds(1800))
            .createdBy(studentUserId)
            .updatedBy(studentUserId)
            .build();
    }

    @Test
    @DisplayName(
        "sweep: when lock is held by another node, skips execution (FR-02)"
    )
    void sweep_WhenLockHeld_Skips() {
        when(
            valueOperations.setIfAbsent(
                eq("lock:sweeper:registration-reconciliation"),
                eq("1"),
                eq(Duration.ofSeconds(240))
            )
        ).thenReturn(false);

        sweeper.sweep();

        verify(registrationRepository, never()).findExpiredPendingRegistrations(
            any(),
            anyInt()
        );
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName(
        "sweep: when no expired registrations, releases lock and exits (FR-03)"
    )
    void sweep_WhenNoExpiredRegistrations_ReleasesLock() {
        when(
            registrationRepository.findExpiredPendingRegistrations(
                any(),
                eq(100)
            )
        ).thenReturn(List.of());

        sweeper.sweep();

        verify(redisTemplate).delete(
            "lock:sweeper:registration-reconciliation"
        );
        verifyNoInteractions(registrationPaymentPort);
    }

    @Test
    @DisplayName(
        "sweep: Case A - Delayed Webhook Recovery confirms registration and publishes events (FR-06, FR-07)"
    )
    void sweep_CaseA_DelayedWebhookRecovery() {
        Registration reg = buildPendingRegistration(registrationId);
        when(
            registrationRepository.findExpiredPendingRegistrations(
                any(),
                eq(100)
            )
        ).thenReturn(List.of(reg));
        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );

        PaymentStatusResult statusResult = new PaymentStatusResult(
            registrationId,
            "pi_success_1",
            "succeeded",
            new BigDecimal("50.00"),
            "USD",
            null,
            null
        );
        when(
            registrationPaymentPort.getPaymentStatus(registrationId)
        ).thenReturn(Optional.of(statusResult));

        RegistrationConfirmedDto confirmedDto = new RegistrationConfirmedDto(
            registrationId,
            workshopId,
            studentUserId,
            "{\"qr\":\"sample\"}",
            Instant.now()
        );
        when(
            registrationApi.confirmPaidRegistration(
                any(ConfirmPaidRegistrationCommand.class)
            )
        ).thenReturn(confirmedDto);

        IdempotencyKeyJpaEntity keyEntity = new IdempotencyKeyJpaEntity();
        keyEntity.setId(idempotencyKeyId);
        keyEntity.setStatus("PENDING");
        when(idempotencyKeyJpaRepository.findById(idempotencyKeyId)).thenReturn(
            Optional.of(keyEntity)
        );

        sweeper.sweep();

        verify(registrationApi).confirmPaidRegistration(
            any(ConfirmPaidRegistrationCommand.class)
        );
        verify(registrationPaymentPort).markPaymentCompleted(
            registrationId,
            "pi_success_1",
            new BigDecimal("50.00"),
            "USD"
        );

        ArgumentCaptor<IdempotencyKeyJpaEntity> keyCaptor =
            ArgumentCaptor.forClass(IdempotencyKeyJpaEntity.class);
        verify(idempotencyKeyJpaRepository).save(keyCaptor.capture());
        assertThat(keyCaptor.getValue().getStatus()).isEqualTo("COMPLETED");

        verify(realtimeEventPublisher).publishToUser(eq(studentUserId), any());
        verify(realtimeEventPublisher).publishToWorkshop(eq(workshopId), any());
        verify(redisTemplate).delete(
            "lock:sweeper:registration-reconciliation"
        );
    }

    @Test
    @DisplayName(
        "sweep: Case B - Standard Cleanup cancels PaymentIntent, fails registration, and cleans Redis idempotency (FR-08, FR-09)"
    )
    void sweep_CaseB_StandardCleanup() {
        Registration reg = buildPendingRegistration(registrationId);
        when(
            registrationRepository.findExpiredPendingRegistrations(
                any(),
                eq(100)
            )
        ).thenReturn(List.of(reg));
        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );

        PaymentStatusResult statusResult = new PaymentStatusResult(
            registrationId,
            "pi_abandoned_1",
            "requires_payment_method",
            new BigDecimal("50.00"),
            "USD",
            null,
            null
        );
        when(
            registrationPaymentPort.getPaymentStatus(registrationId)
        ).thenReturn(Optional.of(statusResult));

        PaymentStatusResult cancelResult = new PaymentStatusResult(
            null,
            "pi_abandoned_1",
            "canceled",
            new BigDecimal("50.00"),
            "USD",
            null,
            null
        );
        when(
            registrationPaymentPort.cancelPaymentAttempt("pi_abandoned_1")
        ).thenReturn(cancelResult);

        when(
            registrationApi.failPaidRegistration(
                any(FailPaidRegistrationCommand.class)
            )
        ).thenReturn(true);

        IdempotencyKeyJpaEntity keyEntity = new IdempotencyKeyJpaEntity();
        keyEntity.setId(idempotencyKeyId);
        keyEntity.setUserId(studentUserId);
        keyEntity.setIdempotencyKey("idem-key-1");
        keyEntity.setStatus("PENDING");
        when(idempotencyKeyJpaRepository.findById(idempotencyKeyId)).thenReturn(
            Optional.of(keyEntity)
        );

        sweeper.sweep();

        verify(registrationPaymentPort).cancelPaymentAttempt("pi_abandoned_1");
        verify(registrationApi).failPaidRegistration(
            any(FailPaidRegistrationCommand.class)
        );
        verify(registrationPaymentPort).markPaymentFailed(
            eq(registrationId),
            anyString()
        );

        ArgumentCaptor<IdempotencyKeyJpaEntity> keyCaptor =
            ArgumentCaptor.forClass(IdempotencyKeyJpaEntity.class);
        verify(idempotencyKeyJpaRepository).save(keyCaptor.capture());
        assertThat(keyCaptor.getValue().getStatus()).isEqualTo("FAILED");

        verify(redisTemplate).delete(
            "idempotency:" + studentUserId + ":idem-key-1"
        );
        verifyNoInteractions(realtimeEventPublisher);
        verify(redisTemplate).delete(
            "lock:sweeper:registration-reconciliation"
        );
    }

    @Test
    @DisplayName(
        "sweep: cancel revealing payment has succeeded dynamically converts to Case A (FR-12)"
    )
    void sweep_CancelRevealsPaymentSucceeded_DynamicallyProcessesAsCaseA() {
        Registration reg = buildPendingRegistration(registrationId);
        when(
            registrationRepository.findExpiredPendingRegistrations(
                any(),
                eq(100)
            )
        ).thenReturn(List.of(reg));
        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );

        PaymentStatusResult statusResult = new PaymentStatusResult(
            registrationId,
            "pi_race_1",
            "requires_payment_method",
            new BigDecimal("50.00"),
            "USD",
            null,
            null
        );
        when(
            registrationPaymentPort.getPaymentStatus(registrationId)
        ).thenReturn(Optional.of(statusResult));

        PaymentStatusResult cancelSucceededResult = new PaymentStatusResult(
            null,
            "pi_race_1",
            "succeeded",
            new BigDecimal("50.00"),
            "USD",
            null,
            null
        );
        when(
            registrationPaymentPort.cancelPaymentAttempt("pi_race_1")
        ).thenReturn(cancelSucceededResult);

        RegistrationConfirmedDto confirmedDto = new RegistrationConfirmedDto(
            registrationId,
            workshopId,
            studentUserId,
            "{\"qr\":\"sample\"}",
            Instant.now()
        );
        when(
            registrationApi.confirmPaidRegistration(
                any(ConfirmPaidRegistrationCommand.class)
            )
        ).thenReturn(confirmedDto);

        sweeper.sweep();

        verify(registrationApi).confirmPaidRegistration(
            any(ConfirmPaidRegistrationCommand.class)
        );
        verify(registrationPaymentPort).markPaymentCompleted(
            registrationId,
            "pi_race_1",
            new BigDecimal("50.00"),
            "USD"
        );
        verify(registrationPaymentPort, never()).markPaymentFailed(
            any(),
            any()
        );
        verify(realtimeEventPublisher).publishToUser(eq(studentUserId), any());
        verify(realtimeEventPublisher).publishToWorkshop(eq(workshopId), any());
    }

    @Test
    @DisplayName(
        "sweep: expired PENDING registration without payment record resolves as Case B directly (FR-13)"
    )
    void sweep_ExpiredPendingWithoutPaymentRecord_ResolvesAsCaseB() {
        Registration reg = buildPendingRegistration(registrationId);
        when(
            registrationRepository.findExpiredPendingRegistrations(
                any(),
                eq(100)
            )
        ).thenReturn(List.of(reg));
        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(reg)
        );

        when(
            registrationPaymentPort.getPaymentStatus(registrationId)
        ).thenReturn(Optional.empty());

        when(
            registrationApi.failPaidRegistration(
                any(FailPaidRegistrationCommand.class)
            )
        ).thenReturn(true);

        sweeper.sweep();

        verify(registrationPaymentPort, never()).cancelPaymentAttempt(any());
        verify(registrationApi).failPaidRegistration(
            any(FailPaidRegistrationCommand.class)
        );
        verify(registrationPaymentPort, never()).markPaymentFailed(
            any(),
            any()
        );
    }

    @Test
    @DisplayName(
        "sweep: payment status query failure or circuit open leaves registration PENDING (FR-10)"
    )
    void sweep_GatewayUnreachable_LeavesPending() {
        Registration reg = buildPendingRegistration(registrationId);
        when(
            registrationRepository.findExpiredPendingRegistrations(
                any(),
                eq(100)
            )
        ).thenReturn(List.of(reg));

        when(
            registrationPaymentPort.getPaymentStatus(registrationId)
        ).thenThrow(new ServiceUnavailableException("Stripe unreachable", 30));

        sweeper.sweep();

        verify(registrationApi, never()).confirmPaidRegistration(any());
        verify(registrationApi, never()).failPaidRegistration(any());
        verify(registrationPaymentPort, never()).markPaymentCompleted(
            any(),
            any(),
            any(),
            any()
        );
        verify(registrationPaymentPort, never()).markPaymentFailed(
            any(),
            any()
        );
    }

    @Test
    @DisplayName(
        "sweep: payment still processing leaves registration PENDING (FR-11)"
    )
    void sweep_PaymentProcessing_LeavesPending() {
        Registration reg = buildPendingRegistration(registrationId);
        when(
            registrationRepository.findExpiredPendingRegistrations(
                any(),
                eq(100)
            )
        ).thenReturn(List.of(reg));

        PaymentStatusResult statusResult = new PaymentStatusResult(
            registrationId,
            "pi_proc_1",
            "processing",
            new BigDecimal("50.00"),
            "USD",
            null,
            null
        );
        when(
            registrationPaymentPort.getPaymentStatus(registrationId)
        ).thenReturn(Optional.of(statusResult));

        sweeper.sweep();

        verify(registrationPaymentPort, never()).cancelPaymentAttempt(any());
        verify(registrationApi, never()).confirmPaidRegistration(any());
        verify(registrationApi, never()).failPaidRegistration(any());
    }

    @Test
    @DisplayName(
        "sweep: webhook race condition defense skips when registration is no longer PENDING (FR-14)"
    )
    void sweep_WebhookRaceCondition_Skips() {
        Registration reg = buildPendingRegistration(registrationId);
        when(
            registrationRepository.findExpiredPendingRegistrations(
                any(),
                eq(100)
            )
        ).thenReturn(List.of(reg));

        PaymentStatusResult statusResult = new PaymentStatusResult(
            registrationId,
            "pi_100",
            "succeeded",
            new BigDecimal("50.00"),
            "USD",
            null,
            null
        );
        when(
            registrationPaymentPort.getPaymentStatus(registrationId)
        ).thenReturn(Optional.of(statusResult));

        // When retrieved inside transaction, registration has already been CONFIRMED by webhook
        Registration alreadyConfirmed = reg
            .toBuilder()
            .status(RegistrationStatus.CONFIRMED)
            .build();
        when(registrationRepository.findById(registrationId)).thenReturn(
            Optional.of(alreadyConfirmed)
        );

        sweeper.sweep();

        verify(registrationApi, never()).confirmPaidRegistration(any());
        verify(registrationPaymentPort, never()).markPaymentCompleted(
            any(),
            any(),
            any(),
            any()
        );
        verifyNoInteractions(realtimeEventPublisher);
    }

    @Test
    @DisplayName(
        "sweep: failure processing one registration does not abort remaining batch (FR-04)"
    )
    void sweep_IndividualFailureDoesNotAbortBatch() {
        UUID reg1Id = UUID.randomUUID();
        UUID reg2Id = UUID.randomUUID();
        Registration reg1 = buildPendingRegistration(reg1Id);
        Registration reg2 = buildPendingRegistration(reg2Id);

        when(
            registrationRepository.findExpiredPendingRegistrations(
                any(),
                eq(100)
            )
        ).thenReturn(List.of(reg1, reg2));

        // reg1 throws unexpected exception during status check
        when(registrationPaymentPort.getPaymentStatus(reg1Id)).thenThrow(
            new RuntimeException("Simulated unexpected failure")
        );

        // reg2 succeeds as Case A
        when(registrationRepository.findById(reg2Id)).thenReturn(
            Optional.of(reg2)
        );
        PaymentStatusResult reg2Result = new PaymentStatusResult(
            reg2Id,
            "pi_2",
            "succeeded",
            new BigDecimal("50.00"),
            "USD",
            null,
            null
        );
        when(registrationPaymentPort.getPaymentStatus(reg2Id)).thenReturn(
            Optional.of(reg2Result)
        );

        RegistrationConfirmedDto confirmedDto = new RegistrationConfirmedDto(
            reg2Id,
            workshopId,
            studentUserId,
            "{\"qr\":\"sample2\"}",
            Instant.now()
        );
        when(
            registrationApi.confirmPaidRegistration(
                any(ConfirmPaidRegistrationCommand.class)
            )
        ).thenReturn(confirmedDto);

        sweeper.sweep();

        // reg2 should still be confirmed despite reg1 failing
        verify(registrationApi).confirmPaidRegistration(
            any(ConfirmPaidRegistrationCommand.class)
        );
        verify(registrationPaymentPort).markPaymentCompleted(
            reg2Id,
            "pi_2",
            new BigDecimal("50.00"),
            "USD"
        );
    }
}
