package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpeakerResponse(
    UUID id,
    String name,
    String email,
    String bio,
    String avatarUrl,
    String linkedinUrl,
    Integer sortOrder
) {}
