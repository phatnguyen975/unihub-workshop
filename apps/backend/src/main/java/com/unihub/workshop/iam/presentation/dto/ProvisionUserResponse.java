package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvisionUserResponse(
    UUID userId,
    String email,
    String fullName,
    String role,
    UUID organizationId
) {}
