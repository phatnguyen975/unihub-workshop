package com.unihub.workshop.shared.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Standard cursor pagination metadata container for REST API responses,
 * strictly conformant to {@code docs/api-standards.md}.
 *
 * <p>Produces JSON:
 * <pre>{@code
 * "cursor": {
 *   "nextCursor": "...",
 *   "hasMore": false
 * }
 * }</pre>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CursorResponse(String nextCursor, boolean hasMore) {
    public static CursorResponse of(String nextCursor, boolean hasMore) {
        return new CursorResponse(nextCursor, hasMore);
    }
}
