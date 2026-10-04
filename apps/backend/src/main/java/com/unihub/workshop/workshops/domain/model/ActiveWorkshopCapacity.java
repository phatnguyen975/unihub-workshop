package com.unihub.workshop.workshops.domain.model;

import java.util.UUID;

public record ActiveWorkshopCapacity(UUID id, Integer capacity) {
    public int capacityValue() {
        return capacity != null ? Math.max(0, capacity) : 0;
    }
}
