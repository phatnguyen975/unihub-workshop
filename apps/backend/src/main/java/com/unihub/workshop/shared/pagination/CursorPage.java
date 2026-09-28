package com.unihub.workshop.shared.pagination;

import java.util.List;

/**
 * Pure Java generic container for cursor-based paginated results across all business modules,
 * used at the domain, application port, and persistence adapter layers.
 *
 * @param <T> the type of items on the page
 * @param items the list of items for the current page window
 * @param nextCursor the opaque cursor string for fetching the subsequent page, or null if no further pages
 * @param hasMore true if additional items exist beyond the current page
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {
    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
