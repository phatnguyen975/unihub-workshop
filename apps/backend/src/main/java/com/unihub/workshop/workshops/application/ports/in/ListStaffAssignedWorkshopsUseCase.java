package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;

public interface ListStaffAssignedWorkshopsUseCase {
    CursorPage<StaffAssignedWorkshop> execute(
        ListStaffAssignedWorkshopsQuery query
    );
}
