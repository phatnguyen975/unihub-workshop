package com.unihub.workshop.workers.search_sync.core.ports.out;

import java.util.UUID;

/**
 * Outbound port for seeding the atomic capacity counter workshop:{id}:capacity in Redis.
 */
public interface WorkshopCapacityStore {
    void set(UUID workshopId, int capacity);
}
