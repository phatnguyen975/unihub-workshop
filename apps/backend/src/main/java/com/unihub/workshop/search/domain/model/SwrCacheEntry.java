package com.unihub.workshop.search.domain.model;

import com.unihub.workshop.shared.pagination.CursorPage;
import java.time.Instant;

public record SwrCacheEntry(
    Instant cachedAt,
    CursorPage<WorkshopCard> result
) {}
