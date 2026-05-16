package com.unihub.workshop.workshops.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateWorkshopRequest(
        @NotBlank String title,
        String description,
        @NotBlank String location,
        String venue,
        String room,
        String roomLayoutKey,
        @Min(1) int capacityTotal,
        @NotNull BigDecimal priceAmount,
        String currency,
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime) {
}
