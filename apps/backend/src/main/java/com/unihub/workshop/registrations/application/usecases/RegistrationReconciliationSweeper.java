package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.FailPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.PaymentStatusResult;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.idempotency.IdempotencyKeyJpaRepository;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Scheduled background sweeper for dual-case reconciliation of expired PENDING registrations.
 * Queries Stripe outside database transactions to distinguish delayed webhook payments (Case A)
 * from abandoned checkouts (Case B), ensuring accurate state transitions and capacity restoration.
 */
@Slf4j
@Component
public class RegistrationReconciliationSweeper {

    private static final String LOCK_KEY =
        "lock:sweeper:registration-reconciliation";
    private static final Duration LOCK_TTL = Duration.ofSeconds(240);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
        new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class
        );

    private final RegistrationRepository registrationRepository;
    private final RegistrationPaymentPort registrationPaymentPort;
    private final RegistrationApi registrationApi;
    private final StringRedisTemplate redisTemplate;
    private final IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final TransactionOperations transactionOperations;
    private final int batchSize;

    public RegistrationReconciliationSweeper(
        RegistrationRepository registrationRepository,
        RegistrationPaymentPort registrationPaymentPort,
        RegistrationApi registrationApi,
        StringRedisTemplate redisTemplate,
        IdempotencyKeyJpaRepository idempotencyKeyJpaRepository,
        RealtimeEventPublisher realtimeEventPublisher,
        TransactionOperations transactionOperations,
        @Value("${app.registrations.sweeper.batch-size:100}") int batchSize
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationPaymentPort = registrationPaymentPort;
        this.registrationApi = registrationApi;
        this.redisTemplate = redisTemplate;
        this.idempotencyKeyJpaRepository = idempotencyKeyJpaRepository;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.transactionOperations = transactionOperations;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.registrations.sweeper.cron:0 */30 * * * *}")
    public void sweep() {
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            acquired = redisTemplate
                .opsForValue()
                .setIfAbsent(LOCK_KEY, lockToken, LOCK_TTL);
        } catch (Exception e) {
            log.warn(
                "Failed to check or acquire distributed lock '{}': {}",
                LOCK_KEY,
                e.getMessage()
            );
            return;
        }

        if (!Boolean.TRUE.equals(acquired)) {
            log.debug(
                "Reconciliation sweeper skipped: lock '{}' is currently held by another node",
                LOCK_KEY
            );
            return;
        }

        try {
            executeSweep();
        } finally {
            releaseLock(lockToken);
        }
    }

    /** Outcome of processing a single expired registration in one sweep cycle. */
    private enum SweeperResult {
        CONFIRMED,
        FAILED,
        SKIPPED,
    }

    private void executeSweep() {
        Instant now = Instant.now();
        List<Registration> expiredRegistrations =
            registrationRepository.findExpiredPendingRegistrations(
                now,
                batchSize
            );

        if (expiredRegistrations.isEmpty()) {
            log.debug(
                "No expired PENDING registrations found for reconciliation"
            );
            return;
        }

        int selected = expiredRegistrations.size();
        log.info(
            "Found [{}] expired PENDING registrations for reconciliation (batch limit: {})",
            selected,
            batchSize
        );

        int confirmed = 0;
        int failed = 0;
        int skipped = 0;

        for (Registration registration : expiredRegistrations) {
            try {
                SweeperResult result = processRegistration(registration);
                switch (result) {
                    case CONFIRMED -> confirmed++;
                    case FAILED -> failed++;
                    case SKIPPED -> skipped++;
                }
            } catch (Exception e) {
                log.error(
                    "Unexpected error during reconciliation of registration [{}]: {}",
                    registration.id(),
                    e.getMessage(),
                    e
                );
                skipped++;
            }
        }

        log.info(
            "Reconciliation sweeper cycle completed: selected={}, confirmed={}, failed={}, skipped={}",
            selected,
            confirmed,
            failed,
            skipped
        );
    }

    private SweeperResult processRegistration(Registration registration) {
        Optional<PaymentStatusResult> statusOpt;
        try {
            // Query payment gateway outside DB transactions
            statusOpt = registrationPaymentPort.getPaymentStatus(
                registration.id()
            );
        } catch (CallNotPermittedException e) {
            log.warn(
                "Stripe circuit breaker is OPEN for registration [{}], leaving PENDING",
                registration.id()
            );
            return SweeperResult.SKIPPED;
        } catch (Exception e) {
            log.error(
                "Payment status query failed for registration [{}], leaving PENDING: {}",
                registration.id(),
                e.getMessage()
            );
            return SweeperResult.SKIPPED;
        }

        // Expired PENDING registration without payment record
        if (statusOpt.isEmpty()) {
            log.info(
                "Expired PENDING registration [{}] has no payment record, resolving directly as Case B",
                registration.id()
            );
            return executeCaseB(
                registration,
                null,
                "No payment attempt was created before expiration"
            );
        }

        PaymentStatusResult paymentStatus = statusOpt.get();

        // Payment is still processing
        if (paymentStatus.isProcessing()) {
            log.info(
                "Payment for registration [{}] is still processing, leaving PENDING",
                registration.id()
            );
            return SweeperResult.SKIPPED;
        }

        // Case A: Delayed Webhook Recovery
        if (paymentStatus.isSucceeded()) {
            log.warn(
                "Payment for registration [{}] succeeded but webhook was not received; executing Case A Delayed Webhook Recovery (lost webhook detected)",
                registration.id()
            );
            return executeCaseA(registration, paymentStatus)
                ? SweeperResult.CONFIRMED
                : SweeperResult.SKIPPED;
        }

        // Case B: Payment failed, uninitiated, abandoned, or canceled
        PaymentStatusResult cancelResult;
        try {
            // Request gateway to cancel outstanding payment attempt outside DB transactions
            cancelResult = registrationPaymentPort.cancelPaymentAttempt(
                paymentStatus.providerPaymentId()
            );
        } catch (CallNotPermittedException e) {
            log.warn(
                "Stripe circuit breaker is OPEN for payment cancel on registration [{}], leaving PENDING",
                registration.id()
            );
            return SweeperResult.SKIPPED;
        } catch (Exception e) {
            log.error(
                "Payment cancel failed for registration [{}], leaving PENDING: {}",
                registration.id(),
                e.getMessage()
            );
            return SweeperResult.SKIPPED;
        }

        // If cancel reveals payment has meanwhile succeeded, dynamically process as Case A
        if (cancelResult != null && cancelResult.isSucceeded()) {
            log.warn(
                "Payment cancel revealed payment has succeeded for registration [{}]; executing Case A Delayed Webhook Recovery (lost webhook detected)",
                registration.id()
            );
            return executeCaseA(registration, cancelResult)
                ? SweeperResult.CONFIRMED
                : SweeperResult.SKIPPED;
        } else {
            log.info(
                "Payment cancel confirmed for registration [{}], executing Case B Standard Cleanup",
                registration.id()
            );
            return executeCaseB(
                registration,
                paymentStatus,
                "Payment attempt canceled or abandoned"
            );
        }
    }

    private boolean executeCaseA(
        Registration registration,
        PaymentStatusResult paymentStatus
    ) {
        RegistrationConfirmedDto confirmedDto = transactionOperations.execute(
            status -> {
                // Defense against webhook race condition
                Optional<Registration> currentOpt =
                    registrationRepository.findById(registration.id());
                if (
                    currentOpt.isEmpty() ||
                    currentOpt.get().status() != RegistrationStatus.PENDING
                ) {
                    log.info(
                        "Registration [{}] is no longer PENDING, skipping Case A transition",
                        registration.id()
                    );
                    return null;
                }

                ConfirmPaidRegistrationCommand confirmCmd =
                    new ConfirmPaidRegistrationCommand(
                        registration.id(),
                        paymentStatus.providerPaymentId()
                    );
                RegistrationConfirmedDto dto =
                    registrationApi.confirmPaidRegistration(confirmCmd);
                if (dto == null) {
                    return null;
                }

                // Transition payment to COMPLETED
                registrationPaymentPort.markPaymentCompleted(
                    registration.id(),
                    paymentStatus.providerPaymentId(),
                    paymentStatus.amount(),
                    paymentStatus.currency()
                );

                // Mark registration's idempotency key COMPLETED and evict Redis L1 cache
                // (symmetric with Case B eviction — ensures client retries get a fresh COMPLETED response)
                if (registration.idempotencyKeyId() != null) {
                    idempotencyKeyJpaRepository
                        .findById(registration.idempotencyKeyId())
                        .ifPresent(key -> {
                            key.setStatus("COMPLETED");
                            idempotencyKeyJpaRepository.save(key);
                            evictIdempotencyKeyFromRedis(
                                key.getUserId(),
                                key.getIdempotencyKey()
                            );
                        });
                }

                return dto;
            }
        );

        // Post-commit real-time events
        if (confirmedDto == null) {
            return false;
        }
        publishRealtimeEventsCaseA(confirmedDto);
        return true;
    }

    private SweeperResult executeCaseB(
        Registration registration,
        PaymentStatusResult paymentStatus,
        String failureReason
    ) {
        Boolean failed = transactionOperations.execute(status -> {
            // Defense against webhook race condition
            Optional<Registration> currentOpt = registrationRepository.findById(
                registration.id()
            );
            if (
                currentOpt.isEmpty() ||
                currentOpt.get().status() != RegistrationStatus.PENDING
            ) {
                log.info(
                    "Registration [{}] is no longer PENDING, skipping Case B transition",
                    registration.id()
                );
                return false;
            }

            FailPaidRegistrationCommand failCmd =
                new FailPaidRegistrationCommand(
                    registration.id(),
                    failureReason
                );
            boolean transitionApplied = registrationApi.failPaidRegistration(
                failCmd
            );
            if (!transitionApplied) {
                return false;
            }

            // Transition payment to FAILED if payment record existed.
            if (paymentStatus != null) {
                registrationPaymentPort.markPaymentFailed(
                    registration.id(),
                    failureReason
                );
            }

            // Mark registration's idempotency key FAILED and evict Redis L1 cache
            if (registration.idempotencyKeyId() != null) {
                idempotencyKeyJpaRepository
                    .findById(registration.idempotencyKeyId())
                    .ifPresent(key -> {
                        key.setStatus("FAILED");
                        idempotencyKeyJpaRepository.save(key);
                        evictIdempotencyKeyFromRedis(
                            key.getUserId(),
                            key.getIdempotencyKey()
                        );
                    });
            }
            return true;
        });
        if (!Boolean.TRUE.equals(failed)) {
            return SweeperResult.SKIPPED;
        }
        return SweeperResult.FAILED;
    }

    private void releaseLock(String lockToken) {
        try {
            redisTemplate.execute(
                RELEASE_LOCK_SCRIPT,
                List.of(LOCK_KEY),
                lockToken
            );
        } catch (Exception e) {
            log.warn(
                "Failed to release distributed lock '{}': {}",
                LOCK_KEY,
                e.getMessage()
            );
        }
    }

    /**
     * Evicts the Redis L1 idempotency cache entry for a given user and key.
     * Called by both Case A (COMPLETED) and Case B (FAILED) to ensure consistency
     * between the DB idempotency record and the in-memory Redis cache.
     */
    private void evictIdempotencyKeyFromRedis(
        UUID userId,
        String idempotencyKey
    ) {
        try {
            String redisKey = "idempotency:" + userId + ":" + idempotencyKey;
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn(
                "Failed to evict Redis idempotency key for user [{}]: {}",
                userId,
                e.getMessage()
            );
        }
    }

    private void publishRealtimeEventsCaseA(
        RegistrationConfirmedDto confirmedDto
    ) {
        try {
            // 1. payment.confirmed to student's personal stream: unihub:stream:user:{studentUserId}
            Map<String, Object> userPayload = Map.of(
                "registrationId",
                confirmedDto.registrationId(),
                "workshopId",
                confirmedDto.workshopId(),
                "qrPayload",
                confirmedDto.qrPayload() != null
                    ? confirmedDto.qrPayload()
                    : "",
                "qrIssuedAt",
                confirmedDto.qrIssuedAt() != null
                    ? confirmedDto.qrIssuedAt().toString()
                    : ""
            );
            RealtimeEvent<Map<String, Object>> userEvent = RealtimeEvent.of(
                RealtimeEventType.PAYMENT_CONFIRMED,
                userPayload
            );
            realtimeEventPublisher.publishToUser(
                confirmedDto.studentUserId(),
                userEvent
            );

            // 2. registration.updated to workshop's stream: unihub:stream:workshop:{workshopId}
            Map<String, Object> workshopPayload = Map.of(
                "workshopId",
                confirmedDto.workshopId(),
                "status",
                "CONFIRMED"
            );
            RealtimeEvent<Map<String, Object>> workshopEvent = RealtimeEvent.of(
                RealtimeEventType.REGISTRATION_UPDATED,
                workshopPayload
            );
            realtimeEventPublisher.publishToWorkshop(
                confirmedDto.workshopId(),
                workshopEvent
            );
        } catch (Exception e) {
            log.error(
                "Failed to publish post-commit realtime events for registration [{}]: {}",
                confirmedDto.registrationId(),
                e.getMessage(),
                e
            );
        }
    }
}
