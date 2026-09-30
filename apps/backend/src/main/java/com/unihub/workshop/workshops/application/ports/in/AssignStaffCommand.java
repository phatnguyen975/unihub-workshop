package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.exception.BadRequestException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public record AssignStaffCommand(
    UUID workshopId,
    UUID organizationId,
    UUID callerId,
    List<UUID> staffUserIds
) {
    public AssignStaffCommand {
        if (workshopId == null) {
            throw new BadRequestException("Workshop ID is required");
        }
        if (organizationId == null) {
            throw new BadRequestException("Organization ID is required");
        }
        if (callerId == null) {
            throw new BadRequestException("Caller ID is required");
        }
        if (staffUserIds == null || staffUserIds.isEmpty()) {
            throw new BadRequestException("staffUserIds must not be empty");
        }
        if (staffUserIds.size() > 50) {
            throw new BadRequestException(
                "staffUserIds must not exceed 50 items"
            );
        }
        // Deduplicate while preserving order
        staffUserIds = List.copyOf(new LinkedHashSet<>(staffUserIds));
    }
}
