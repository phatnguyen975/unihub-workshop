package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.exception.BadRequestException;
import java.util.UUID;

public record RevokeStaffCommand(
    UUID workshopId,
    UUID staffUserId,
    UUID organizationId,
    UUID callerId
) {
    public RevokeStaffCommand {
        if (workshopId == null) {
            throw new BadRequestException("Workshop ID is required");
        }
        if (staffUserId == null) {
            throw new BadRequestException("Staff user ID is required");
        }
        if (organizationId == null) {
            throw new BadRequestException("Organization ID is required");
        }
        if (callerId == null) {
            throw new BadRequestException("Caller ID is required");
        }
    }
}
