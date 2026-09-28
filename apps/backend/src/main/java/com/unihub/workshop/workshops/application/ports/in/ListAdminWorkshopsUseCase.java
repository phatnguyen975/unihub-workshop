package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.domain.model.WorkshopSummary;

public interface ListAdminWorkshopsUseCase {
    CursorPage<WorkshopSummary> execute(ListAdminWorkshopsQuery query);
}
