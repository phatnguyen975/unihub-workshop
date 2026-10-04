package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicSpeakerResponse(
    UUID id,
    String name,
    String bio,
    String avatarUrl,
    String linkedinUrl,
    String email,
    Integer sortOrder
) {}
