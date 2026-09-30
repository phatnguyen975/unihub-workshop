package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.domain.model.AvailableStaff;

public interface ListAvailableStaffUseCase {
    CursorPage<AvailableStaff> execute(ListAvailableStaffQuery query);
}
