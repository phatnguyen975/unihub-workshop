package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.application.ports.in.RegisterWorkshopUseCase;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.strategy.RegistrationStrategy;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final List<RegistrationStrategy> strategies;

    @Override
    @Transactional
    public Registration register(
        UUID studentUserId,
        UUID workshopId,
        UUID idempotencyKeyId
    ) {
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
        if (workshop.startsAt() == null || !workshop.startsAt().isAfter(now)) {
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
                "Active registration already exists for this workshop"
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

        // 7. Dispatch to selected strategy
        return selectedStrategy.register(student, workshop, idempotencyKeyId);
    }
}
