package com.unihub.workshop.workshops.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignSpeakerRequest(
        @NotBlank String name,
        String bio,
        @Min(0) Integer sortOrder,
        @NotNull Integer version) {
}
