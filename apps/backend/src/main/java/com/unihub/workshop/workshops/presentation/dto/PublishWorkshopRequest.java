package com.unihub.workshop.workshops.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record PublishWorkshopRequest(
    @NotNull(message = "version: must not be null") Integer version
) {}
