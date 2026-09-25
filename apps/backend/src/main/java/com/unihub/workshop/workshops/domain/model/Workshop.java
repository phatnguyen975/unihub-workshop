package com.unihub.workshop.workshops.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record Workshop(
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
    BigDecimal price,
    String currency,
    WorkshopStatus status,
    Integer version,
    Instant publishedAt,
    Instant canceledAt,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public static Workshop createDraft(
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
        BigDecimal price,
        String currency,
        UUID createdBy
    ) {
        Instant now = Instant.now();
        return Workshop.builder()
            .id(UUID.randomUUID())
            .organizationId(organizationId)
            .title(title)
            .slug(slug)
            .description(description)
            .coverImageUrl(coverImageUrl)
            .location(location)
            .timezone(
                timezone != null && !timezone.isBlank()
                    ? timezone
                    : "Asia/Ho_Chi_Minh"
            )
            .startsAt(startsAt)
            .endsAt(endsAt)
            .capacity(capacity)
            .price(price != null ? price : BigDecimal.ZERO)
            .currency(
                currency != null && !currency.isBlank() ? currency : "USD"
            )
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(createdBy)
            .updatedBy(createdBy)
            .build();
    }

    public Workshop updateDraft(
        String title,
        String slug,
        String description,
        String coverImageUrl,
        String location,
        String timezone,
        Instant startsAt,
        Instant endsAt,
        Integer capacity,
        BigDecimal price,
        String currency,
        UUID updatedBy
    ) {
        if (this.status != WorkshopStatus.DRAFT) {
            throw new com.unihub.workshop.shared.exception.ConflictException(
                "INVALID_WORKSHOP_STATUS",
                "Only workshops in DRAFT status can be updated as a draft"
            );
        }
        Instant now = Instant.now();
        return this.toBuilder()
            .title(title)
            .slug(slug != null ? slug : this.slug)
            .description(description)
            .coverImageUrl(coverImageUrl)
            .location(location)
            .timezone(
                timezone != null && !timezone.isBlank()
                    ? timezone
                    : this.timezone != null
                      ? this.timezone
                      : "Asia/Ho_Chi_Minh"
            )
            .startsAt(startsAt)
            .endsAt(endsAt)
            .capacity(capacity)
            .price(
                price != null
                    ? price
                    : this.price != null
                      ? this.price
                      : BigDecimal.ZERO
            )
            .currency(
                currency != null && !currency.isBlank()
                    ? currency
                    : this.currency != null
                      ? this.currency
                      : "USD"
            )
            .updatedAt(now)
            .updatedBy(updatedBy)
            .build();
    }
}
