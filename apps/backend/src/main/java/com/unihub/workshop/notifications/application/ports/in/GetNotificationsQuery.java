package com.unihub.workshop.notifications.application.ports.in;

import com.unihub.workshop.shared.exception.ValidationException;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record GetNotificationsQuery(
    UUID userId,
    Boolean isRead,
    String cursor,
    int size
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;

    public static GetNotificationsQuery of(
        UUID userId,
        Boolean isRead,
        String cursor,
        Integer size
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (size != null) {
            if (size < MIN_SIZE) {
                throw new ValidationException(
                    "Validation failed for query parameters.",
                    List.of("size: must be greater than or equal to 1")
                );
            }
            if (size > MAX_SIZE) {
                throw new ValidationException(
                    "Validation failed for query parameters.",
                    List.of("size: must be less than or equal to 100")
                );
            }
        }
        int resolvedSize = size != null ? size : DEFAULT_SIZE;
        String resolvedCursor =
            cursor != null && !cursor.isBlank() ? cursor.trim() : null;

        return new GetNotificationsQuery(
            userId,
            isRead,
            resolvedCursor,
            resolvedSize
        );
    }
}
