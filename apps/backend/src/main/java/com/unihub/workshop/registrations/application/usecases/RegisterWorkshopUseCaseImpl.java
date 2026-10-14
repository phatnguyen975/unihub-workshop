package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.application.ports.in.RegisterWorkshopUseCase;
import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.strategy.RegistrationStrategy;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.idempotency.ClaimResult;
import com.unihub.workshop.shared.idempotency.IdempotencyPort;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orchestrator service implementing {@link RegisterWorkshopUseCase}.
 * Validates student eligibility, workshop state, schedule conflicts, and dispatches to appropriate strategy.
 */
@Service
@RequiredArgsConstructor
public class RegisterWorkshopUseCaseImpl implements RegisterWorkshopUseCase {

    private final IamApi iamApi;
    private final WorkshopApi workshopApi;
    private final RegistrationRepository registrationRepository;
    private final IdempotencyPort idempotencyPort;
    private final CapacityPort capacityPort;
    private final List<RegistrationStrategy> strategies;

    @Override
    public Registration register(
        UUID studentUserId,
        UUID workshopId,
        String idempotencyKey
    ) {
        String requestHash = computeRequestHash(workshopId);

        ClaimResult<Registration> claimResult = idempotencyPort.claim(
            studentUserId,
            idempotencyKey,
            "POST",
            "/api/v1/registrations",
            requestHash,
            Registration.class
        );

        if (
            claimResult instanceof ClaimResult.Replayed<Registration> replayed
        ) {
            return replayed.response();
        }

        ClaimResult.Acquired<Registration> acquired =
            (ClaimResult.Acquired<Registration>) claimResult;
        UUID idempotencyKeyRecordId = acquired.idempotencyKeyId();

        boolean capacityAcquired = false;
        try {
            // 1. Student university record linkage check
            StudentProfileDto student = iamApi
                .getStudentProfile(studentUserId)
                .filter(profile -> profile.studentRecordId() != null)
                .orElseThrow(() ->
                    new ForbiddenException(
                        "Student university profile is not linked"
                    )
                );

            // 2. Workshop existence check
            WorkshopRegistrationDetailsDto workshop = workshopApi
                .getWorkshopForRegistration(workshopId)
                .orElseThrow(() ->
                    new NotFoundException("Workshop not found: " + workshopId)
                );

            // 3. Workshop status and schedule validation
            if (!workshop.isPublished()) {
                throw new ValidationException(
                    "workshopId",
                    "Workshop is not published"
                );
            }

            Instant now = Instant.now();
            if (
                workshop.startsAt() == null || !workshop.startsAt().isAfter(now)
            ) {
                throw new ValidationException(
                    "startsAt",
                    "Workshop has already started or invalid schedule"
                );
            }

            // 4. Strategy selection
            RegistrationStrategy selectedStrategy = strategies
                .stream()
                .filter(strategy -> strategy.supports(workshop))
                .findFirst()
                .orElseThrow(() ->
                    new ValidationException(
                        "price",
                        "Paid workshop registration requires checkout flow"
                    )
                );

            // 5. Duplicate active registration check
            if (
                registrationRepository.existsActiveRegistration(
                    studentUserId,
                    workshopId
                )
            ) {
                throw new ConflictException(
                    "Student is already registered for this workshop."
                );
            }

            // 6. Schedule conflict check
            if (
                registrationRepository.hasOverlappingActiveRegistration(
                    studentUserId,
                    workshop.startsAt(),
                    workshop.endsAt()
                )
            ) {
                throw new ValidationException(
                    "Schedule conflict: you have an overlapping active registration"
                );
            }

            // 7. Acquire capacity
            int totalCapacity =
                workshop.capacity() != null ? workshop.capacity() : 0;
            capacityAcquired = capacityPort.tryAcquireCapacity(
                workshopId,
                totalCapacity,
                () ->
                    (int) (totalCapacity -
                        registrationRepository.countActiveRegistrations(
                            workshopId
                        ))
            );

            if (!capacityAcquired) {
                throw new ConflictException(
                    "No seats remaining for this workshop."
                );
            }

            // 8. Execute strategy & complete idempotency
            Registration saved = selectedStrategy.register(
                student,
                workshop,
                idempotencyKeyRecordId
            );
            idempotencyPort.complete(
                studentUserId,
                idempotencyKeyRecordId,
                idempotencyKey,
                requestHash,
                201,
                saved
            );

            return saved;
        } catch (RuntimeException ex) {
            if (capacityAcquired) {
                capacityPort.releaseCapacity(workshopId);
            }
            idempotencyPort.fail(studentUserId, idempotencyKey);
            throw ex;
        }
    }

    private static String computeRequestHash(UUID workshopId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                ("{\"workshopId\":\"" + workshopId + "\"}").getBytes(
                    StandardCharsets.UTF_8
                )
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
