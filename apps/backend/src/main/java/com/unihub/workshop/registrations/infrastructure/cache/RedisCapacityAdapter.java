package com.unihub.workshop.registrations.infrastructure.cache;

import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import java.util.UUID;
import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis adapter for lock-free atomic capacity pre-checks and compensating rollback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCapacityAdapter implements CapacityPort {

    private static final String CAPACITY_KEY_PREFIX = "workshop:";
    private static final String CAPACITY_KEY_SUFFIX = ":capacity";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquireCapacity(
        UUID workshopId,
        int totalCapacity,
        IntSupplier activeRegistrationsSupplier
    ) {
        if (workshopId == null) {
            return false;
        }

        String key = formatKey(workshopId);
        try {
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.FALSE.equals(exists)) {
                int activeCount =
                    activeRegistrationsSupplier != null
                        ? activeRegistrationsSupplier.getAsInt()
                        : 0;
                int groundTruth = Math.max(0, totalCapacity - activeCount);
                redisTemplate
                    .opsForValue()
                    .setIfAbsent(key, String.valueOf(groundTruth));
            }

            Long remaining = redisTemplate.opsForValue().decrement(key);
            if (remaining == null) {
                throw new ServiceUnavailableException(
                    "Failed to decrement workshop capacity in Redis",
                    30
                );
            }

            if (remaining < 0) {
                // Compensating increment to restore the counter
                redisTemplate.opsForValue().increment(key);
                return false;
            }

            return true;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                "Redis capacity check failed for workshop {}: {}",
                workshopId,
                e.getMessage(),
                e
            );
            throw new ServiceUnavailableException(
                "Redis is unavailable for capacity verification",
                30,
                e
            );
        }
    }

    @Override
    public void releaseCapacity(UUID workshopId) {
        if (workshopId == null) {
            return;
        }

        String key = formatKey(workshopId);
        try {
            redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn(
                "Failed to release Redis capacity for workshop {}: {}",
                workshopId,
                e.getMessage()
            );
        }
    }

    private String formatKey(UUID workshopId) {
        return CAPACITY_KEY_PREFIX + workshopId + CAPACITY_KEY_SUFFIX;
    }
}
