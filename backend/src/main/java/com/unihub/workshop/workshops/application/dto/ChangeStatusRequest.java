package com.unihub.workshop.workshops.application.dto;

import jakarta.validation.constraints.NotNull;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;

public record ChangeStatusRequest(
        @NotNull WorkshopStatus status,
        String reason) {
}
