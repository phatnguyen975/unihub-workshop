package com.unihub.workshop.workshops.application.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record RescheduleWorkshopRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        String location) {
}
