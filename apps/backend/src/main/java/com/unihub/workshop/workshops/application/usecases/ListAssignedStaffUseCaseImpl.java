package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListAssignedStaffQuery;
import com.unihub.workshop.workshops.application.ports.in.ListAssignedStaffUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.AssignedStaff;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAssignedStaffUseCaseImpl implements ListAssignedStaffUseCase {

    private final WorkshopRepository workshopRepository;
    private final WorkshopAssignmentRepository assignmentRepository;
    private final IamApi iamApi;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<AssignedStaff> execute(ListAssignedStaffQuery query) {
        Workshop workshop = workshopRepository
            .findById(query.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found: " + query.workshopId()
                )
            );

        if (!workshop.organizationId().equals(query.organizationId())) {
            throw new ForbiddenException(
                "Cannot access workshop belonging to a different organization"
            );
        }

        CursorPage<WorkshopAssignment> assignmentPage =
            assignmentRepository.findByWorkshopId(
                query.workshopId(),
                query.cursor(),
                query.size()
            );

        if (
            assignmentPage.items() == null || assignmentPage.items().isEmpty()
        ) {
            return new CursorPage<>(
                Collections.emptyList(),
                assignmentPage.nextCursor(),
                assignmentPage.hasMore()
            );
        }

        List<UUID> staffUserIds = assignmentPage
            .items()
            .stream()
            .map(WorkshopAssignment::staffUserId)
            .toList();

        Map<UUID, StaffUserDto> staffProfiles = iamApi.getStaffUsersByIds(
            staffUserIds
        );

        List<AssignedStaff> assignedStaffList = assignmentPage
            .items()
            .stream()
            .map(assignment -> {
                StaffUserDto profile = staffProfiles.get(
                    assignment.staffUserId()
                );
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

        return new CursorPage<>(
            assignedStaffList,
            assignmentPage.nextCursor(),
            assignmentPage.hasMore()
        );
    }
}
