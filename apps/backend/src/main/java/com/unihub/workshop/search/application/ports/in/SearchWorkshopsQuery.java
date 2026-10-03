package com.unihub.workshop.search.application.ports.in;

import com.unihub.workshop.shared.exception.ValidationException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import lombok.Builder;

@Builder(toBuilder = true)
public record SearchWorkshopsQuery(
    String q,
    String priceType,
    Instant from,
    Instant to,
    String sort,
    String cursor,
    int size
) {
    public static final String DEFAULT_SORT = "startsAt,asc";
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final Set<String> ALLOWED_PRICE_TYPES = Set.of(
        "FREE",
        "PAID"
    );
    private static final Set<String> ALLOWED_SORTS = Set.of(
        "startsAt,asc",
        "startsAt,desc",
        "price,asc",
        "price,desc",
        "capacity,asc",
        "capacity,desc"
    );

    public SearchWorkshopsQuery {
        if (sort == null || sort.isBlank()) {
            sort = DEFAULT_SORT;
        }
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
    }

    public static SearchWorkshopsQuery of(
        String q,
        String priceType,
        String fromStr,
        String toStr,
        String sort,
        String cursor,
        Integer size
    ) {
        validateParams(q, priceType, sort, size);
        Instant from = parseInstant(fromStr, "from");
        Instant to = parseInstant(toStr, "to");
        validateDateRange(from, to);

        String trimmedQ = q != null && !q.isBlank() ? q.trim() : null;
        String resolvedSort =
            sort != null && !sort.isBlank() ? sort : DEFAULT_SORT;
        int resolvedSize = size != null ? size : DEFAULT_SIZE;

        return new SearchWorkshopsQuery(
            trimmedQ,
            priceType,
            from,
            to,
            resolvedSort,
            cursor,
            resolvedSize
        );
    }

    private static void validateParams(
        String q,
        String priceType,
        String sort,
        Integer size
    ) {
        if (q != null && q.length() > 200) {
            throw new ValidationException(
                "q",
                "must not exceed 200 characters"
            );
        }
        if (size != null && (size < 1 || size > 100)) {
            throw new ValidationException("size", "must be between 1 and 100");
        }
        if (
            priceType != null &&
            !priceType.isBlank() &&
            !ALLOWED_PRICE_TYPES.contains(priceType)
        ) {
            throw new ValidationException(
                "priceType",
                "must be one of FREE, PAID"
            );
        }
        if (sort != null && !sort.isBlank() && !ALLOWED_SORTS.contains(sort)) {
            throw new ValidationException(
                "sort",
                "must be one of startsAt,asc, startsAt,desc, price,asc, price,desc, capacity,asc, capacity,desc"
            );
        }
    }

    private static Instant parseInstant(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException(
                paramName,
                "must be a valid ISO-8601 UTC timestamp"
            );
        }
    }

    private static void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ValidationException(
                "from",
                "must be strictly earlier than to"
            );
        }
    }
}
