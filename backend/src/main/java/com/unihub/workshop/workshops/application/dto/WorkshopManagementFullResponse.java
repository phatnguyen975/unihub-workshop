package com.unihub.workshop.workshops.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;

public record WorkshopManagementFullResponse(
        UUID id,
        UUID organizerId,
        String title,
        String description,
        String location,
        String venue,
        String room,
        String roomLayoutKey,
        WorkshopStatus status,
        int version,
        int capacityTotal,
        int capacityRemaining,
        BigDecimal priceAmount,
        String currency,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<WorkshopSpeakerResponse> speakers,
        List<AssignedStaffResponse> assignedStaff,
        String brochureUrl) {
}
