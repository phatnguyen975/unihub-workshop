package com.unihub.workshop.workshops.application.ports.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record SaveDraftCommand(
    UUID id,
    Integer version,
    UUID organizationId,
    UUID callerId,
    String title,
    String description,
    String coverImageUrl,
    String location,
    String timezone,
    Instant startsAt,
    Instant endsAt,
    Integer capacity,
    BigDecimal price,
    String currency
) {}
