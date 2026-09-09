package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.UserRole;

import java.util.UUID;

public record ProvisionUserCommand(
        UUID organizationId,
        UUID callerId,
        String email,
        String fullName,
        String phoneNumber,
        UserRole role) {
}
