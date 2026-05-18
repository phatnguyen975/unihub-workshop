package com.unihub.workshop.workshops.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

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
        @NotNull OffsetDateTime endTime,
        List<@Valid SpeakerRequest> speakers) {
}
