package com.unihub.workshop.workshops.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    UUID updatedBy,
    List<Speaker> speakers
) {
    public Workshop {
        if (speakers == null) {
            speakers = List.of();
        }
    }

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
        return createDraft(
            organizationId,
            title,
            slug,
            description,
            coverImageUrl,
            location,
            timezone,
            startsAt,
            endsAt,
            capacity,
            price,
            currency,
            createdBy,
            List.of()
        );
    }

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
        UUID createdBy,
        List<Speaker> speakers
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
            .speakers(speakers != null ? List.copyOf(speakers) : List.of())
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
        return updateDraft(
            title,
            slug,
            description,
            coverImageUrl,
            location,
            timezone,
            startsAt,
            endsAt,
            capacity,
            price,
            currency,
            updatedBy,
            null
        );
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
        UUID updatedBy,
        List<Speaker> speakers
    ) {
        if (this.status != WorkshopStatus.DRAFT) {
            throw new com.unihub.workshop.shared.exception.ConflictException(
                "INVALID_WORKSHOP_STATUS",
                "Only workshops in DRAFT status can be updated as a draft"
            );
        }
        Instant now = Instant.now();
        List<Speaker> effectiveSpeakers =
            speakers != null
                ? List.copyOf(speakers)
                : this.speakers != null
                  ? this.speakers
                  : List.of();

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
            .speakers(effectiveSpeakers)
            .build();
    }
}
