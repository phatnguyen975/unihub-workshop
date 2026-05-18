package com.unihub.workshop.workshops.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SpeakerRequest(
        @NotBlank String name,
        String bio,
        @Min(0) Integer sortOrder) {
}
