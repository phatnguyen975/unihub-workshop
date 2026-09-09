package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.User;

import java.util.UUID;

public interface GetOrganizationMemberUseCase {
    User execute(UUID organizationId, UUID memberUserId);
}
