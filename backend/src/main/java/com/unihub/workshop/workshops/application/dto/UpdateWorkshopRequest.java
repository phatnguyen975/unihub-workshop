package com.unihub.workshop.workshops.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpdateWorkshopRequest(
        @NotBlank String title,
        String description,
        @NotBlank String location,
        String venue,
        String room,
        String roomLayoutKey,
        @Min(1) int capacityTotal,
        BigDecimal priceAmount,
        String currency,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        int version) {
}
