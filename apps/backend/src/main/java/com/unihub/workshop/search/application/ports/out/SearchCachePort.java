package com.unihub.workshop.search.application.ports.out;

import com.unihub.workshop.search.domain.model.SwrCacheEntry;
import java.time.Duration;
import java.util.Optional;

public interface SearchCachePort {
    Optional<SwrCacheEntry> get(String key);

    void put(String key, SwrCacheEntry entry, Duration hardTtl);

    boolean tryLock(String lockKey, Duration lockTimeout);

    void releaseLock(String lockKey);
}
