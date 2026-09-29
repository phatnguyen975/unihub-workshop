package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.shared.events.StaffAssignedToWorkshopEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.AssignStaffCommand;
import com.unihub.workshop.workshops.application.ports.in.AssignStaffUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.AssignedStaff;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignStaffUseCaseImpl implements AssignStaffUseCase {

    private final WorkshopRepository workshopRepository;
    private final WorkshopAssignmentRepository assignmentRepository;
    private final IamApi iamApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public List<AssignedStaff> execute(AssignStaffCommand command) {
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
                "Workshop must be in PUBLISHED status before assigning staff."
            );
        }

        // 4. Workshop has not started: now() < starts_at (422)
        Instant now = Instant.now();
        if (workshop.startsAt() == null || !now.isBefore(workshop.startsAt())) {
            throw new ValidationException(
                "Cannot assign staff after the workshop has started.",
                List.of("startsAt: Workshop has already started")
            );
        }

        // 5. Staff members valid: exist, role STAFF, same org, account active (422)
        Map<UUID, StaffUserDto> staffMap = iamApi.getStaffUsersByIds(
            command.staffUserIds()
        );
        List<String> invalidStaffErrors = new ArrayList<>();
        for (UUID staffId : command.staffUserIds()) {
            StaffUserDto staff = staffMap.get(staffId);
            if (
                staff == null ||
                !command.organizationId().equals(staff.organizationId()) ||
                !"STAFF".equalsIgnoreCase(staff.role()) ||
                !staff.isActive()
            ) {
                invalidStaffErrors.add(
                    "staffUserIds: Staff user " +
                        staffId +
                        " does not exist, is inactive, does not hold the STAFF role, or belongs to another organization"
                );
            }
        }
        if (!invalidStaffErrors.isEmpty()) {
            throw new ValidationException(
                "Validation failed for one or more staff members in the batch.",
                invalidStaffErrors
            );
        }

        // 6. Not already assigned to this workshop (409)
        Set<UUID> alreadyAssigned =
            assignmentRepository.findAlreadyAssignedUserIds(
                workshop.id(),
                command.staffUserIds()
            );
        if (!alreadyAssigned.isEmpty()) {
            throw new ConflictException(
                "One or more staff members are already assigned to this workshop."
            );
        }

        // 7. No schedule overlap with active workshops (409)
        Set<UUID> conflictingStaff =
            assignmentRepository.findScheduleConflictingUserIds(
                workshop.organizationId(),
                workshop.id(),
                workshop.startsAt(),
                workshop.endsAt(),
                command.staffUserIds()
            );
        if (!conflictingStaff.isEmpty()) {
            throw new ConflictException(
                "One or more staff members have an overlapping schedule with another active workshop."
            );
        }

        // 8. Atomically insert assignment rows
        List<WorkshopAssignment> assignmentsToCreate = command
            .staffUserIds()
            .stream()
            .map(staffId ->
                WorkshopAssignment.create(
                    workshop.id(),
                    staffId,
                    command.callerId()
                )
            )
            .toList();

        List<WorkshopAssignment> savedAssignments;
        try {
            savedAssignments = assignmentRepository.saveAll(
                assignmentsToCreate
            );
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                "One or more staff members are already assigned to this workshop."
            );
        }

        // 9. Publish integration events via Transactional Outbox
        for (WorkshopAssignment assignment : savedAssignments) {
            eventPublisher.publishEvent(
                new StaffAssignedToWorkshopEvent(
                    assignment.id(),
                    workshop.id(),
                    assignment.staffUserId(),
                    workshop.organizationId(),
                    command.callerId(),
                    workshop.title(),
                    workshop.startsAt(),
                    workshop.endsAt(),
                    workshop.location()
                )
            );
        }

        // 10. Map to domain AssignedStaff projections
        return savedAssignments
            .stream()
            .map(assignment -> {
                StaffUserDto profile = staffMap.get(assignment.staffUserId());
                return AssignedStaff.builder()
                    .userId(assignment.staffUserId())
                    .fullName(profile != null ? profile.fullName() : null)
                    .email(profile != null ? profile.email() : null)
                    .avatarUrl(profile != null ? profile.avatarUrl() : null)
                    .phoneNumber(profile != null ? profile.phoneNumber() : null)
                    .assignedAt(assignment.assignedAt())
                    .build();
            })
            .toList();
    }
}
