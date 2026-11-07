package com.unihub.workshop.attendances.application.ports.in;

import java.util.UUID;

public record GetAttendanceSummaryQuery(UUID workshopId, UUID organizationId) {}
