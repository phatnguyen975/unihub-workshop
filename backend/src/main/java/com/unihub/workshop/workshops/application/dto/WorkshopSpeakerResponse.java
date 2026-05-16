package com.unihub.workshop.workshops.application.dto;

import java.util.UUID;

public record WorkshopSpeakerResponse(
        UUID id,
        String name,
        String bio,
        int sortOrder) {
}
