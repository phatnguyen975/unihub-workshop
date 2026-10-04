package com.unihub.workshop.workers.search_sync.outbound.cache;

import com.unihub.workshop.workers.search_sync.core.ports.out.WorkshopCapacityStore;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWorkshopCapacityAdapter implements WorkshopCapacityStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void set(UUID workshopId, int capacity) {
        if (workshopId == null) {
            return;
        }
        try {
            String key = "workshop:" + workshopId + ":capacity";
            redisTemplate.opsForValue().set(key, String.valueOf(capacity));
            log.info(
                "Initialized Redis capacity counter for workshop {} to {}",
                workshopId,
                capacity
            );
        } catch (Exception e) {
            log.error(
                "Failed to initialize Redis capacity counter for workshop {}: {}",
                workshopId,
                e.getMessage(),
                e
            );
        }
    }
}
