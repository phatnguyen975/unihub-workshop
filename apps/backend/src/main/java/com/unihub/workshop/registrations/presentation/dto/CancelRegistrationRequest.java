package com.unihub.workshop.registrations.presentation.dto;

import jakarta.validation.constraints.Size;

public record CancelRegistrationRequest(
    @Size(
        max = 1000,
        message = "Cancellation reason must not exceed 1000 characters"
    )
    String reason
) {}
