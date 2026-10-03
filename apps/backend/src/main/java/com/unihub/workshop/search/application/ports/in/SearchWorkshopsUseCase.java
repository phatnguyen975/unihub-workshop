package com.unihub.workshop.search.application.ports.in;

import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.shared.pagination.CursorPage;

public interface SearchWorkshopsUseCase {
    CursorPage<WorkshopCard> search(SearchWorkshopsQuery query);
}
