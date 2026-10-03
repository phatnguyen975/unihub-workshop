package com.unihub.workshop.search.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkshopCard(
    UUID id,
    String slug,
    String title,
    String coverImageUrl,
    String location,
    Instant startsAt,
    Instant endsAt,
    String timezone,
    BigDecimal price,
    String currency,
    Integer capacity,
    Integer availableSeats,
    String priceType,
    UUID organizationId
) {
    public static String derivePriceType(BigDecimal price) {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0
            ? "PAID"
            : "FREE";
    }
}
