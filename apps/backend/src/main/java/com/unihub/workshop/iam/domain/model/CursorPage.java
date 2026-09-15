package com.unihub.workshop.iam.domain.model;

import java.util.List;
import lombok.Builder;

@Builder
public record CursorPage<T>(
    List<T> items,
    String nextCursor,
    boolean hasMore
) {}
