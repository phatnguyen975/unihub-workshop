package com.unihub.workshop.iam.presentation.dto;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegisterResponse(UUID userId) {
}
