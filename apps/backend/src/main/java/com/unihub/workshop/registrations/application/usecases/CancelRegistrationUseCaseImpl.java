package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.RefundPaymentCommand;
import com.unihub.workshop.registrations.api.dto.RefundPaymentResult;
import com.unihub.workshop.registrations.application.ports.in.CancelRegistrationCommand;
import com.unihub.workshop.registrations.application.ports.in.CancelRegistrationUseCase;
import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.ports.out.WorkshopRegistrationBroadcaster;
import com.unihub.workshop.registrations.domain.model.CancelledRegistrationResult;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.events.RegistrationCanceledEvent;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.idempotency.ClaimResult;
import com.unihub.workshop.shared.idempotency.IdempotencyPort;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelRegistrationUseCaseImpl
    implements CancelRegistrationUseCase
{

    private final RegistrationRepository registrationRepository;
    private final WorkshopApi workshopApi;
    private final IamApi iamApi;
    private final IdempotencyPort idempotencyPort;
    private final CapacityPort capacityPort;
    private final WorkshopRegistrationBroadcaster workshopRegistrationBroadcaster;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactionOperations;
    private final RegistrationPaymentPort registrationPaymentPort;

    @Override
    public CancelledRegistrationResult cancel(
        CancelRegistrationCommand command
    ) {
        // 1. Reason validation and normalization
        String normalizedReason = null;
        if (command.reason() != null) {
            String trimmed = command.reason().trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > 1000) {
                    throw new ValidationException(
                        "reason",
                        "Cancellation reason must not exceed 1000 characters"
                    );
                }
                normalizedReason = trimmed;
            }
        }

        // 2. Compute request hash and claim idempotency key
        String requestHash = computeRequestHash(
            command.registrationId(),
            normalizedReason
        );
        ClaimResult<CancelledRegistrationResult> claimResult =
            idempotencyPort.claim(
                command.callerUserId(),
                command.idempotencyKey(),
                "POST",
                "/api/v1/registrations/" + command.registrationId() + "/cancel",
                requestHash,
                CancelledRegistrationResult.class
            );

        if (
            claimResult instanceof
                ClaimResult.Replayed<CancelledRegistrationResult> replayed
        ) {
            return replayed.response();
        }

        ClaimResult.Acquired<CancelledRegistrationResult> acquired =
            (ClaimResult.Acquired<CancelledRegistrationResult>) claimResult;
        UUID idempotencyKeyRecordId = acquired.idempotencyKeyId();

        try {
            // 3. Registration existence check
            Registration registration = registrationRepository
                .findById(command.registrationId())
                .orElseThrow(() ->
                    new NotFoundException(
                        "Registration not found: " + command.registrationId()
                    )
                );

            // 4. Workshop existence check
            WorkshopRegistrationDetailsDto workshop = workshopApi
                .getWorkshopForRegistration(registration.workshopId())
                .orElseThrow(() ->
                    new NotFoundException(
                        "Workshop not found: " + registration.workshopId()
                    )
                );

            // 5. Evaluate RBAC before state and time validation
            validateAuthorization(command, registration, workshop);

            // 6. Registration status validation
            validateRegistrationStatus(registration);

            // 7. Workshop status & schedule timing validation
            validateWorkshopState(workshop);

            Instant now = Instant.now();

            // 8. Cancellation execution: Free vs Paid
            if (
                registration.priceAtRegistration().compareTo(BigDecimal.ZERO) ==
                0
            ) {
                return executeFreeCancellation(
                    command,
                    registration,
                    workshop,
                    normalizedReason,
                    idempotencyKeyRecordId,
                    requestHash,
                    now
                );
            } else {
                return executePaidCancellation(
                    command,
                    registration,
                    workshop,
                    normalizedReason,
                    idempotencyKeyRecordId,
                    requestHash,
                    now
                );
            }
        } catch (RuntimeException ex) {
            idempotencyPort.fail(
                command.callerUserId(),
                command.idempotencyKey()
            );
            throw ex;
        }
    }

    private void validateAuthorization(
        CancelRegistrationCommand command,
        Registration registration,
        WorkshopRegistrationDetailsDto workshop
    ) {
        if ("STUDENT".equalsIgnoreCase(command.callerRole())) {
            if (!registration.studentUserId().equals(command.callerUserId())) {
                throw new ForbiddenException(
                    "You do not have permission to cancel this registration"
                );
            }
        } else if ("ORGANIZER".equalsIgnoreCase(command.callerRole())) {
            if (
                command.callerOrganizationId() == null ||
                !workshop
                    .organizationId()
                    .equals(command.callerOrganizationId())
            ) {
                throw new ForbiddenException(
                    "You do not have permission to cancel this registration"
                );
            }
        } else {
            throw new ForbiddenException(
                "You do not have permission to cancel this registration"
            );
        }
    }

    private void validateRegistrationStatus(Registration registration) {
        if (registration.status() == RegistrationStatus.CANCELED) {
            throw new ValidationException(
                "status",
                "Registration is already canceled"
            );
        }
        if (registration.status() == RegistrationStatus.FAILED) {
            throw new ValidationException(
                "status",
                "Cannot cancel a failed registration"
            );
        }
        if (registration.status() == RegistrationStatus.PENDING) {
            throw new ValidationException(
                "status",
                "Only CONFIRMED registrations can be canceled"
            );
        }
        if (registration.status() != RegistrationStatus.CONFIRMED) {
            throw new ValidationException(
                "status",
                "Only CONFIRMED registrations can be canceled"
            );
        }
    }

    private void validateWorkshopState(
        WorkshopRegistrationDetailsDto workshop
    ) {
        if (!workshop.isPublished()) {
            throw new ValidationException(
                "workshopId",
                "Workshop is not in a cancelable state"
            );
        }
        Instant now = Instant.now();
        if (workshop.startsAt() == null || !workshop.startsAt().isAfter(now)) {
            throw new ValidationException(
                "startsAt",
                "Cannot cancel registration for a workshop that has already started or ended"
            );
        }
    }

    private CancelledRegistrationResult executeFreeCancellation(
        CancelRegistrationCommand command,
        Registration registration,
        WorkshopRegistrationDetailsDto workshop,
        String normalizedReason,
        UUID idempotencyKeyRecordId,
        String requestHash,
        Instant now
    ) {
        CancelledRegistrationResult result = transactionOperations.execute(
            txStatus -> {
                boolean updated = registrationRepository.cancelRegistration(
                    registration.id(),
                    command.callerUserId(),
                    now
                );

                if (!updated) {
                    Registration fresh = registrationRepository
                        .findById(registration.id())
                        .orElse(registration);
                    if (fresh.status() == RegistrationStatus.CANCELED) {
                        throw new ValidationException(
                            "status",
                            "Registration is already canceled"
                        );
                    }
                    throw new ValidationException(
                        "status",
                        "Only CONFIRMED registrations can be canceled"
                    );
                }

                Registration canceled = registration.cancel(
                    command.callerUserId(),
                    now
                );

                StudentProfileDto student = iamApi
                    .getStudentProfile(registration.studentUserId())
                    .orElse(null);

                RegistrationCanceledEvent event = new RegistrationCanceledEvent(
                    canceled.id(),
                    canceled.workshopId(),
                    canceled.studentUserId(),
                    student != null ? student.email() : null,
                    student != null ? student.fullName() : null,
                    workshop.title(),
                    workshop.slug(),
                    workshop.startsAt(),
                    workshop.endsAt(),
                    workshop.location(),
                    normalizedReason,
                    command.callerRole(),
                    null,
                    null,
                    now
                );
                eventPublisher.publishEvent(event);

                CancelledRegistrationResult completedResult =
                    new CancelledRegistrationResult(canceled, null, null);
                idempotencyPort.complete(
                    command.callerUserId(),
                    idempotencyKeyRecordId,
                    command.idempotencyKey(),
                    requestHash,
                    200,
                    completedResult
                );
                return completedResult;
            }
        );

        // Post-commit Redis capacity restoration
        restoreCapacity(registration.workshopId());

        // Post-commit Realtime SSE event
        publishRealtimeUpdate(registration.workshopId());

        log.info(
            "Registration [{}] canceled for workshop [{}] by caller [{}] with role [{}] (free, no refund)",
            registration.id(),
            registration.workshopId(),
            command.callerUserId(),
            command.callerRole()
        );

        return result;
    }

    private CancelledRegistrationResult executePaidCancellation(
        CancelRegistrationCommand command,
        Registration registration,
        WorkshopRegistrationDetailsDto workshop,
        String normalizedReason,
        UUID idempotencyKeyRecordId,
        String requestHash,
        Instant now
    ) {
        // 1. Verify associated payment record exists in COMPLETED status
        if (!registrationPaymentPort.hasCompletedPayment(registration.id())) {
            log.error(
                "Paid registration [{}] has no payment record in COMPLETED status (FR-34)",
                registration.id()
            );
            throw new InternalServerException(
                "Paid registration has no payment record in COMPLETED status: " +
                    registration.id()
            );
        }

        // 2. Request 100% refund from Stripe outside DB transaction using deterministic idempotency key
        String refundIdempotencyKey = "refund-reg-" + registration.id();
        RefundPaymentCommand refundCommand = new RefundPaymentCommand(
            registration.id(),
            registration.priceAtRegistration(),
            registration.currency(),
            refundIdempotencyKey,
            normalizedReason
        );
        RefundPaymentResult refundResult =
            registrationPaymentPort.refundPayment(refundCommand);

        // 3. Atomically in one DB transaction: transition registration to CANCELED, payment to REFUNDED, outbox event
        CancelledRegistrationResult result = transactionOperations.execute(
            txStatus -> {
                boolean updated = registrationRepository.cancelRegistration(
                    registration.id(),
                    command.callerUserId(),
                    now
                );

                if (!updated) {
                    Registration fresh = registrationRepository
                        .findById(registration.id())
                        .orElse(registration);
                    if (fresh.status() == RegistrationStatus.CANCELED) {
                        throw new ValidationException(
                            "status",
                            "Registration is already canceled"
                        );
                    }
                    throw new ValidationException(
                        "status",
                        "Only CONFIRMED registrations can be canceled"
                    );
                }

                registrationPaymentPort.markPaymentRefunded(
                    registration.id(),
                    refundResult.providerRefundId(),
                    refundResult.amount(),
                    refundResult.currency()
                );

                Registration canceled = registration.cancel(
                    command.callerUserId(),
                    now
                );

                StudentProfileDto student = iamApi
                    .getStudentProfile(registration.studentUserId())
                    .orElse(null);

                RegistrationCanceledEvent event = new RegistrationCanceledEvent(
                    canceled.id(),
                    canceled.workshopId(),
                    canceled.studentUserId(),
                    student != null ? student.email() : null,
                    student != null ? student.fullName() : null,
                    workshop.title(),
                    workshop.slug(),
                    workshop.startsAt(),
                    workshop.endsAt(),
                    workshop.location(),
                    normalizedReason,
                    command.callerRole(),
                    refundResult.amount(),
                    refundResult.currency(),
                    now
                );
                eventPublisher.publishEvent(event);

                CancelledRegistrationResult completedResult =
                    new CancelledRegistrationResult(
                        canceled,
                        refundResult.amount(),
                        refundResult.currency()
                    );
                idempotencyPort.complete(
                    command.callerUserId(),
                    idempotencyKeyRecordId,
                    command.idempotencyKey(),
                    requestHash,
                    200,
                    completedResult
                );
                return completedResult;
            }
        );

        // 4. Post-commit Redis capacity restoration
        restoreCapacity(registration.workshopId());

        // 5. Post-commit Realtime SSE event
        publishRealtimeUpdate(registration.workshopId());

        log.info(
            "Registration [{}] canceled for workshop [{}] by caller [{}] with role [{}] (refund: {} {})",
            registration.id(),
            registration.workshopId(),
            command.callerUserId(),
            command.callerRole(),
            refundResult.amount(),
            refundResult.currency()
        );

        return result;
    }

    private void restoreCapacity(UUID workshopId) {
        try {
            capacityPort.releaseCapacity(workshopId);
        } catch (Exception e) {
            log.warn(
                "Failed to restore Redis capacity for workshop [{}] after cancellation: {}",
                workshopId,
                e.getMessage()
            );
        }
    }

    private void publishRealtimeUpdate(UUID workshopId) {
        workshopRegistrationBroadcaster.broadcast(workshopId);
    }

    private static String computeRequestHash(
        UUID registrationId,
        String reason
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload =
                "{\"registrationId\":\"" +
                registrationId +
                "\",\"reason\":\"" +
                (reason != null ? reason : "") +
                "\"}";
            byte[] hash = digest.digest(
                payload.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                "SHA-256 algorithm not available",
                e
            );
        }
    }
}
