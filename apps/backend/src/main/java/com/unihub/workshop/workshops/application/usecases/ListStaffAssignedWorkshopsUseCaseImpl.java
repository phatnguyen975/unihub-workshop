package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListStaffAssignedWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.in.ListStaffAssignedWorkshopsUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;
import com.unihub.workshop.workshops.domain.model.StaffWorkshopFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListStaffAssignedWorkshopsUseCaseImpl
    implements ListStaffAssignedWorkshopsUseCase
{

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final WorkshopAssignmentRepository assignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<StaffAssignedWorkshop> execute(
        ListStaffAssignedWorkshopsQuery query
    ) {
        int effectiveSize =
            query.size() != null ? query.size() : DEFAULT_PAGE_SIZE;

        if (effectiveSize < MIN_PAGE_SIZE || effectiveSize > MAX_PAGE_SIZE) {
            throw new BadRequestException(
                "The 'size' parameter must be between 1 and 100."
            );
        }

        StaffWorkshopFilter filter = StaffWorkshopFilter.of(
            query.status(),
            query.from(),
            query.to(),
            query.q()
        );

        return assignmentRepository.findAssignedWorkshopsByStaffUserId(
            query.staffUserId(),
            query.cursor(),
            effectiveSize,
            filter
        );
    }
}
