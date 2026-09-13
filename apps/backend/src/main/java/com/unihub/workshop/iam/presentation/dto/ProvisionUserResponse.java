package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.iam.domain.model.User;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvisionUserResponse(
    UUID userId,
    String email,
    String fullName,
    String role,
    UUID organizationId
) {
    public static ProvisionUserResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new ProvisionUserResponse(
            user.id(),
            user.email(),
            user.fullName(),
            user.role() != null ? user.role().name() : null,
            user.organizationId()
        );
    }
}
