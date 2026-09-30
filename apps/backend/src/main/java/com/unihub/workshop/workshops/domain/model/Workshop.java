package com.unihub.workshop.workshops.domain.model;

import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
            throw new ConflictException(
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

    public Workshop publish(UUID updatedBy) {
        if (this.status != WorkshopStatus.DRAFT) {
            throw new ValidationException(
                "status",
                "Only workshops in DRAFT status can be published. Current status: " +
                    this.status
            );
        }

        List<String> errors = new ArrayList<>();

        if (
            this.title == null ||
            this.title.isBlank() ||
            this.title.length() > 255
        ) {
            errors.add(
                "title: must not be null or blank, and up to 255 characters"
            );
        }

        if (this.startsAt == null) {
            errors.add("startsAt: must not be null");
        }

        if (this.endsAt == null) {
            errors.add("endsAt: must not be null");
        }

        if (
            this.startsAt != null &&
            this.endsAt != null &&
            !this.endsAt.isAfter(this.startsAt)
        ) {
            errors.add("endsAt: endsAt must be strictly after startsAt");
        }

        if (
            this.location == null ||
            this.location.isBlank() ||
            this.location.length() > 255
        ) {
            errors.add(
                "location: must not be null or blank, and up to 255 characters"
            );
        }

        if (
            this.capacity == null ||
            this.capacity < 1 ||
            this.capacity > 1_000_000
        ) {
            errors.add(
                "capacity: must not be null and must be between 1 and 1,000,000"
            );
        }

        Instant now = Instant.now();
        if (this.startsAt != null && !this.startsAt.isAfter(now)) {
            errors.add("startsAt: Workshop start time must be in the future");
        }

        if (!errors.isEmpty()) {
            String primaryMessage =
                errors.size() == 1
                    ? errors.get(0)
                    : "Workshop cannot be published from DRAFT when required fields are missing or invalid.";
            throw new ValidationException(primaryMessage, errors);
        }

        return this.toBuilder()
            .status(WorkshopStatus.PUBLISHED)
            .publishedAt(now)
            .updatedAt(now)
            .updatedBy(updatedBy)
            .build();
    }
}
