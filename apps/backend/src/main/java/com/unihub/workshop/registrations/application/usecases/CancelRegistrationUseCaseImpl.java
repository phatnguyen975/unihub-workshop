package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.application.ports.in.CancelRegistrationCommand;
import com.unihub.workshop.registrations.application.ports.in.CancelRegistrationUseCase;
import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.CancelledRegistrationResult;
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
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
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
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations transactionOperations;

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
                throw new UnsupportedOperationException(
                    "Paid registration cancellation with Stripe refund is handled in Task T3"
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
        if ("STUDENT".equals(command.callerRole())) {
            if (!registration.studentUserId().equals(command.callerUserId())) {
                throw new ForbiddenException(
                    "You do not have permission to cancel this registration"
                );
            }
        } else if ("ORGANIZER".equals(command.callerRole())) {
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
        Registration updatedRegistration = transactionOperations.execute(
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

                return canceled;
            }
        );

        // Post-commit Redis capacity restoration
        try {
            capacityPort.releaseCapacity(registration.workshopId());
        } catch (Exception e) {
            log.warn(
                "Failed to restore Redis capacity for workshop [{}] after cancellation: {}",
                registration.workshopId(),
                e.getMessage()
            );
        }

        // Post-commit Realtime SSE event
        try {
            Map<String, Object> workshopPayload = Map.of(
                "workshopId",
                registration.workshopId(),
                "status",
                "CANCELED"
            );
            RealtimeEvent<Map<String, Object>> workshopEvent = RealtimeEvent.of(
                RealtimeEventType.REGISTRATION_UPDATED,
                workshopPayload
            );
            realtimeEventPublisher.publishToWorkshop(
                registration.workshopId(),
                workshopEvent
            );
        } catch (Exception e) {
            log.error(
                "Failed to publish registration.updated SSE for workshop [{}]: {}",
                registration.workshopId(),
                e.getMessage(),
                e
            );
        }

        // Complete idempotency key durability
        CancelledRegistrationResult result = new CancelledRegistrationResult(
            updatedRegistration,
            null,
            null
        );

        idempotencyPort.complete(
            command.callerUserId(),
            idempotencyKeyRecordId,
            command.idempotencyKey(),
            requestHash,
            200,
            result
        );

        return result;
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
