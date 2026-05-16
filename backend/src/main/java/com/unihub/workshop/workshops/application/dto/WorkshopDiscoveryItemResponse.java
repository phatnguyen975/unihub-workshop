package com.unihub.workshop.workshops.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkshopDiscoveryItemResponse(
        UUID id,
        int version,
        String title,
        String description,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        int capacityTotal,
        int capacityRemaining,
        String status,
        String location,
        String venue,
        String room,
        String roomLayoutKey,
        BigDecimal priceAmount,
        String currency) {
}
