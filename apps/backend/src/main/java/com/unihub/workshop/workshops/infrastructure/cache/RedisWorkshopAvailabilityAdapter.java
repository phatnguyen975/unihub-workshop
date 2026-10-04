package com.unihub.workshop.workshops.infrastructure.cache;

import com.unihub.workshop.workshops.api.WorkshopRegistrationCountPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAvailabilityPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWorkshopAvailabilityAdapter
    implements WorkshopAvailabilityPort
{

    private final StringRedisTemplate redisTemplate;
    private final WorkshopRegistrationCountPort registrationCountPort;

    @Override
    public int resolveAvailableSeats(UUID workshopId, int totalCapacity) {
        if (workshopId == null) {
            return 0;
        }

        String key = "workshop:" + workshopId + ":capacity";
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null && !val.isBlank()) {
                int remaining = Integer.parseInt(val.trim());
                return Math.max(0, remaining);
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value in Redis key '{}'", key);
        } catch (Exception e) {
            log.warn(
                "Redis GET failed for '{}', falling back to DB: {}",
                key,
                e.getMessage()
            );
        }

        // Cache miss or failure: fallback to totalCapacity - countActiveRegistrations
        try {
            long activeCount = registrationCountPort.countActiveRegistrations(
                workshopId
            );
            return (int) Math.max(0, totalCapacity - activeCount);
        } catch (Exception e) {
            log.error(
                "Failed to query active registration count for workshop {}: {}",
                workshopId,
                e.getMessage()
            );
            return Math.max(0, totalCapacity);
        }
    }

    @Override
    public void deleteCapacity(UUID workshopId) {
        if (workshopId == null) {
            return;
        }

        String key = "workshop:" + workshopId + ":capacity";
        try {
            redisTemplate.delete(key);
            log.info("Deleted Redis capacity key '{}'", key);
        } catch (Exception e) {
            log.warn(
                "Failed to delete Redis capacity key '{}': {}",
                key,
                e.getMessage()
            );
        }
    }
}
