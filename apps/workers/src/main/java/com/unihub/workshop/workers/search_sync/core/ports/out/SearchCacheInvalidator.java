package com.unihub.workshop.workers.search_sync.core.ports.out;

/**
 * Outbound port for invalidating Redis search cache keys matching the search:* prefix pattern.
 */
public interface SearchCacheInvalidator {
    void invalidateAll();
}
