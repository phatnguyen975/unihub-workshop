package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.events.StaffRevokedFromWorkshopEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.RevokeStaffCommand;
import com.unihub.workshop.workshops.application.ports.in.RevokeStaffUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RevokeStaffUseCaseImpl implements RevokeStaffUseCase {

    private final WorkshopRepository workshopRepository;
    private final WorkshopAssignmentRepository assignmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void execute(RevokeStaffCommand command) {
        // 1. Workshop exists (404)
        Workshop workshop = workshopRepository
            .findById(command.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found: " + command.workshopId()
                )
            );

        // 2. Organization tenant ownership match (403)
        if (!workshop.organizationId().equals(command.organizationId())) {
            throw new ForbiddenException(
                "Cannot access workshop belonging to a different organization"
            );
        }

        // 3. Workshop status is PUBLISHED (409)
        if (workshop.status() != WorkshopStatus.PUBLISHED) {
            throw new ConflictException(
                "Workshop must be in PUBLISHED status before revoking staff."
            );
        }

        // 4. Workshop has not started: now() < starts_at (422)
        Instant now = Instant.now();
        if (workshop.startsAt() == null || !now.isBefore(workshop.startsAt())) {
            throw new ValidationException(
                "Cannot revoke staff after the workshop has started.",
                List.of("startsAt: Workshop has already started")
            );
        }

        // 5. Staff member is currently assigned to this workshop (404)
        boolean assigned =
            assignmentRepository.existsByWorkshopIdAndStaffUserId(
                command.workshopId(),
                command.staffUserId()
            );
        if (!assigned) {
            throw new NotFoundException(
                "Staff assignment not found for workshop id " +
                    command.workshopId() +
                    " and user id " +
                    command.staffUserId() +
                    "."
            );
        }

        // 6. Delete assignment row
        assignmentRepository.deleteByWorkshopIdAndStaffUserId(
            command.workshopId(),
            command.staffUserId()
        );

        // 7. Publish integration event via Transactional Outbox
        eventPublisher.publishEvent(
            new StaffRevokedFromWorkshopEvent(
                workshop.id(),
                command.staffUserId(),
                workshop.organizationId(),
                command.callerId(),
                workshop.title(),
                workshop.startsAt(),
                workshop.endsAt(),
                workshop.location()
            )
        );
    }
}
