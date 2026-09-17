package com.unihub.workshop.iam.domain.model;

import java.util.List;

public record CursorPage<T>(
    List<T> items,
    String nextCursor,
    boolean hasMore
) {}
