package com.unihub.workshop.workshops.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaveDraftRequest(
    UUID id,
    @Min(value = 0, message = "Version must be non-negative") Integer version,

    @NotBlank(message = "Title must not be blank")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    String title,

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    String description,

    @Size(max = 2048, message = "coverImageUrl must not exceed 2048 characters")
    @Pattern(
        regexp = "^(https?://.*)?$",
        message = "coverImageUrl must be a valid HTTP or HTTPS URL"
    )
    String coverImageUrl,

    @Size(max = 255, message = "Location must not exceed 255 characters")
    String location,

    @Size(max = 64, message = "Timezone must not exceed 64 characters")
    String timezone,

    Instant startsAt,
    Instant endsAt,

    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 1000000, message = "Capacity must not exceed 1,000,000")
    Integer capacity,

    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    @DecimalMax(
        value = "99999999.99",
        message = "Price must not exceed 99,999,999.99"
    )
    @Digits(
        integer = 8,
        fraction = 2,
        message = "Price must have at most 8 integer digits and 2 decimal places"
    )
    BigDecimal price,

    @Pattern(
        regexp = "^([A-Za-z]{3})?$",
        message = "Currency must be a 3-letter ISO 4217 code"
    )
    String currency,

    @Size(max = 10, message = "A workshop cannot have more than 10 speakers")
    List<@Valid SpeakerRequest> speakers
) {}
