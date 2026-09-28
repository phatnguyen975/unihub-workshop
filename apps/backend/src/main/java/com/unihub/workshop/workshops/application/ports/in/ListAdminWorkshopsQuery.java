package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record ListAdminWorkshopsQuery(
    UUID organizationId,
    WorkshopStatus status,
    String priceType,
    String q,
    Instant from,
    Instant to,
    String sort,
    String cursor,
    Integer size
) {}
