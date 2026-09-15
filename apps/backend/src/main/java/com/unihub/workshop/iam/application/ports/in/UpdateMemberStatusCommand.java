package com.unihub.workshop.iam.application.ports.in;

import java.util.UUID;
import lombok.Builder;

@Builder
public record UpdateMemberStatusCommand(
    UUID organizationId,
    UUID callerId,
    UUID targetUserId,
    boolean isActive
) {}
