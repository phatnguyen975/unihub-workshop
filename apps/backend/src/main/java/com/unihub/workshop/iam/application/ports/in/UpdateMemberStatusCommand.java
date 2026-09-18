package com.unihub.workshop.iam.application.ports.in;

import java.util.UUID;

public record UpdateMemberStatusCommand(
    UUID organizationId,
    UUID callerId,
    UUID targetUserId,
    boolean isActive
) {}
