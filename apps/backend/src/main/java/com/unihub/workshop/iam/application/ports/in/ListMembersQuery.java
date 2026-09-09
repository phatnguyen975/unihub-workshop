package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.UserRole;

import java.util.UUID;

public record ListMembersQuery(
        UUID organizationId,
        UUID callerId,
        UserRole role,
        Boolean isActive,
        String search,
        String cursor,
        Integer limit) {
}
