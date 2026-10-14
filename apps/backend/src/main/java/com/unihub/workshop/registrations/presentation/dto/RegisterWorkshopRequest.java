package com.unihub.workshop.registrations.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RegisterWorkshopRequest(
    @NotNull(message = "workshopId is required") UUID workshopId
) {}
