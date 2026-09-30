package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import com.unihub.workshop.workshops.application.ports.in.ListAvailableStaffQuery;
import com.unihub.workshop.workshops.application.ports.in.ListAvailableStaffUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.AvailableStaff;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListAvailableStaffUseCaseImpl
    implements ListAvailableStaffUseCase
{

    private final WorkshopRepository workshopRepository;
    private final WorkshopAssignmentRepository assignmentRepository;
    private final IamApi iamApi;

    private static final Comparator<String> NAME_COMPARATOR =
        Comparator.nullsLast(String::compareToIgnoreCase);

    private static final Comparator<StaffUserDto> STAFF_COMPARATOR =
        Comparator.comparing(
            StaffUserDto::fullName,
            NAME_COMPARATOR
        ).thenComparing(StaffUserDto::userId);

    @Override
    @Transactional(readOnly = true)
    public CursorPage<AvailableStaff> execute(ListAvailableStaffQuery query) {
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

        if (workshop.status() != WorkshopStatus.PUBLISHED) {
            throw new ConflictException(
                "Workshop must be in PUBLISHED status before querying available staff."
            );
        }

        Instant now = Instant.now();
        if (workshop.startsAt() == null || !now.isBefore(workshop.startsAt())) {
            throw new ValidationException(
                "Cannot query available staff after workshop has already started.",
                List.of("startsAt: Workshop has already started")
            );
        }

        // 1. Get all active STAFF members in the organization from IAM
        List<StaffUserDto> orgStaff = iamApi.listActiveStaffByOrganization(
            query.organizationId()
        );
        if (orgStaff == null || orgStaff.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        // 2. Query excluded staff user IDs (already assigned to target workshop + schedule conflicts)
        Set<UUID> alreadyAssigned =
            assignmentRepository.findAssignedStaffUserIds(query.workshopId());
        Set<UUID> conflictingStaff =
            assignmentRepository.findScheduleConflictingStaffUserIds(
                query.organizationId(),
                query.workshopId(),
                workshop.startsAt(),
                workshop.endsAt()
            );

        // 3. Filter eligible staff in application layer
        String qLower = query.q() != null ? query.q().toLowerCase() : null;

        List<StaffUserDto> eligibleStaff = orgStaff
            .stream()
            .filter(s -> !alreadyAssigned.contains(s.userId()))
            .filter(s -> !conflictingStaff.contains(s.userId()))
            .filter(s -> matchesQuery(s, qLower))
            .sorted(STAFF_COMPARATOR)
            .toList();

        // 4. Keyset seek past cursor if cursor provided
        KeysetCursor cursor = KeysetCursorCodec.decode(
            query.cursor(),
            "fullName"
        );
        List<StaffUserDto> afterCursor = eligibleStaff
            .stream()
            .filter(s -> isAfterCursor(s, cursor))
            .toList();

        // 5. Slice page
        int size = query.size();
        boolean hasMore = afterCursor.size() > size;
        List<StaffUserDto> pagedItems = hasMore
            ? afterCursor.subList(0, size)
            : afterCursor;

        String nextCursor = null;
        if (hasMore && !pagedItems.isEmpty()) {
            StaffUserDto last = pagedItems.get(pagedItems.size() - 1);
            nextCursor = KeysetCursorCodec.encode(
                new KeysetCursor(
                    last.userId(),
                    "fullName",
                    last.fullName(),
                    last.fullName()
                )
            );
        }

        // 6. Batch count active assignments for the paged users
        List<UUID> pagedUserIds = pagedItems
            .stream()
            .map(StaffUserDto::userId)
            .toList();
        Map<UUID, Integer> activeCounts = pagedUserIds.isEmpty()
            ? Map.of()
            : assignmentRepository.countActiveAssignmentsForUsers(
                  query.organizationId(),
                  pagedUserIds
              );

        List<AvailableStaff> availableStaff = pagedItems
            .stream()
            .map(s ->
                AvailableStaff.builder()
                    .userId(s.userId())
                    .fullName(s.fullName())
                    .email(s.email())
                    .avatarUrl(s.avatarUrl())
                    .phoneNumber(s.phoneNumber())
                    .activeAssignmentsCount(
                        activeCounts.getOrDefault(s.userId(), 0)
                    )
                    .build()
            )
            .toList();

        return new CursorPage<>(availableStaff, nextCursor, hasMore);
    }

    private boolean matchesQuery(StaffUserDto staff, String qLower) {
        if (qLower == null) {
            return true;
        }
        boolean nameMatch =
            staff.fullName() != null &&
            staff.fullName().toLowerCase().contains(qLower);
        boolean emailMatch =
            staff.email() != null &&
            staff.email().toLowerCase().contains(qLower);
        return nameMatch || emailMatch;
    }

    private boolean isAfterCursor(StaffUserDto staff, KeysetCursor cursor) {
        if (cursor == null) {
            return true;
        }
        String cursorName = cursor.primaryValue();
        UUID cursorId = cursor.id();
        int cmp = NAME_COMPARATOR.compare(staff.fullName(), cursorName);
        if (cmp > 0) {
            return true;
        } else if (cmp == 0) {
            if (cursorId == null) {
                return true;
            }
            return staff.userId().compareTo(cursorId) > 0;
        }
        return false;
    }
}
