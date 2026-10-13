package com.unihub.workshop.workshops.application.ports.in;

import java.util.UUID;

public record GetBrochureSummaryJobQuery(
    UUID workshopId,
    UUID organizationId
) {}
