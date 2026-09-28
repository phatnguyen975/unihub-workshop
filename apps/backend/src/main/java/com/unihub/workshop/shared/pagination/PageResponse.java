package com.unihub.workshop.shared.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.function.Function;
import lombok.Builder;

/**
 * Standard pagination response envelope conforming to {@code docs/api-standards.md}.
 *
 * <p>Produces JSON:
 * <pre>{@code
 * "data": {
 *   "items": [ ... ],
 *   "cursor": {
 *     "nextCursor": "...",
 *     "hasMore": <true|false>
 *   }
 * }
 * }</pre>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(List<T> items, CursorResponse cursor) {
    public static <T> PageResponse<T> of(
        List<T> items,
        String nextCursor,
        boolean hasMore
    ) {
        return new PageResponse<>(
            items,
            CursorResponse.of(nextCursor, hasMore)
        );
    }

    public static <T> PageResponse<T> of(List<T> items, CursorResponse cursor) {
        return new PageResponse<>(items, cursor);
    }

    public static <T> PageResponse<T> from(CursorPage<T> page) {
        if (page == null) {
            return null;
        }
        return of(page.items(), page.nextCursor(), page.hasMore());
    }

    public static <S, T> PageResponse<T> from(
        CursorPage<S> page,
        Function<S, T> mapper
    ) {
        if (page == null) {
            return null;
        }
        List<T> items =
            page.items() != null
                ? page.items().stream().map(mapper).toList()
                : List.of();
        return of(items, page.nextCursor(), page.hasMore());
    }
}
