package com.unihub.workshop.search.application.ports.out;

import com.unihub.workshop.search.application.ports.in.SearchWorkshopsQuery;
import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;

public interface WorkshopSearchRepository {
    CursorPage<WorkshopCard> search(
        SearchWorkshopsQuery query,
        KeysetCursor decodedCursor
    );
}
