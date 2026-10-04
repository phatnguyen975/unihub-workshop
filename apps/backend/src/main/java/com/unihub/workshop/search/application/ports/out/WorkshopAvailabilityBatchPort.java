package com.unihub.workshop.search.application.ports.out;

import com.unihub.workshop.search.domain.model.WorkshopCard;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface WorkshopAvailabilityBatchPort {
    Map<UUID, Integer> resolveAvailableSeats(List<WorkshopCard> cards);
}
