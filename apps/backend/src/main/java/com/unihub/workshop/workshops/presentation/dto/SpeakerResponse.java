package com.unihub.workshop.workshops.presentation.dto;

import java.util.UUID;
import lombok.Builder;

@Builder
public record SpeakerResponse(
    UUID id,
    String name,
    String email,
    String bio,
    String avatarUrl,
    String linkedinUrl
) {}
