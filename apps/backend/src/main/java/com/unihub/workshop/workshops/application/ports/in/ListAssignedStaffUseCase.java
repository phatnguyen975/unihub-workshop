package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.domain.model.AssignedStaff;

public interface ListAssignedStaffUseCase {
    CursorPage<AssignedStaff> execute(ListAssignedStaffQuery query);
}
