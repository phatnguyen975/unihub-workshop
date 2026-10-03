package com.unihub.workshop.workshops.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record PublicWorkshopDetail(
    UUID id,
    UUID organizationId,
    String title,
    String slug,
    String description,
    String coverImageUrl,
    String location,
    String timezone,
    Instant startsAt,
    Instant endsAt,
    Integer capacity,
    int availableSeats,
    BigDecimal price,
    String currency,
    String priceType,
    WorkshopStatus status,
    Instant publishedAt,
    List<Speaker> speakers
) {
    public static PublicWorkshopDetail from(
        Workshop workshop,
        int availableSeats
    ) {
        List<Speaker> sortedSpeakers =
            workshop.speakers() != null
                ? workshop
                      .speakers()
                      .stream()
                      .sorted(
                          Comparator.comparingInt(s ->
                              s.sortOrder() != null ? s.sortOrder() : 0
                          )
                      )
                      .toList()
                : List.of();

        String derivedPriceType =
            workshop.price() != null &&
            workshop.price().compareTo(BigDecimal.ZERO) > 0
                ? "PAID"
                : "FREE";

        return PublicWorkshopDetail.builder()
            .id(workshop.id())
            .organizationId(workshop.organizationId())
            .title(workshop.title())
            .slug(workshop.slug())
            .description(workshop.description())
            .coverImageUrl(workshop.coverImageUrl())
            .location(workshop.location())
            .timezone(workshop.timezone())
            .startsAt(workshop.startsAt())
            .endsAt(workshop.endsAt())
            .capacity(workshop.capacity())
            .availableSeats(Math.max(0, availableSeats))
            .price(workshop.price())
            .currency(workshop.currency())
            .priceType(derivedPriceType)
            .status(workshop.status())
            .publishedAt(workshop.publishedAt())
            .speakers(sortedSpeakers)
            .build();
    }
}
