package com.unihub.workshop.search.infrastructure.cache;

import com.unihub.workshop.search.application.ports.out.WorkshopAvailabilityBatchPort;
import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.workshops.api.WorkshopRegistrationCountPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWorkshopAvailabilityBatchAdapter
    implements WorkshopAvailabilityBatchPort
{

    private final StringRedisTemplate redisTemplate;
    private final WorkshopRegistrationCountPort registrationCountPort;

    @Override
    public Map<UUID, Integer> resolveAvailableSeats(List<WorkshopCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return Map.of();
        }

        List<String> keys = cards
            .stream()
            .map(c -> "workshop:" + c.id() + ":capacity")
            .toList();

        List<String> values;
        try {
            values = redisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.warn(
                "Redis MGET failed for workshop capacities, falling back to database count: {}",
                e.getMessage()
            );
            values = null;
        }

        Map<UUID, Integer> resultMap = new HashMap<>();
        for (int i = 0; i < cards.size(); i++) {
            WorkshopCard card = cards.get(i);
            String val =
                values != null && i < values.size() ? values.get(i) : null;

            if (val != null) {
                try {
                    int remaining = Integer.parseInt(val.trim());
                    resultMap.put(card.id(), Math.max(0, remaining));
                    continue;
                } catch (NumberFormatException e) {
                    log.warn(
                        "Invalid integer value in Redis key 'workshop:{}:capacity': {}",
                        card.id(),
                        val
                    );
                }
            }

            // Cache miss fallback to database active registration count
            int capacity = card.capacity() != null ? card.capacity() : 0;
            try {
                long activeRegistrations =
                    registrationCountPort.countActiveRegistrations(card.id());
                int availableSeats = (int) Math.max(
                    0,
                    capacity - activeRegistrations
                );
                resultMap.put(card.id(), availableSeats);
            } catch (Exception e) {
                log.error(
                    "Failed to query active registration count for workshop {}: {}",
                    card.id(),
                    e.getMessage()
                );
                resultMap.put(card.id(), Math.max(0, capacity));
            }
        }

        return resultMap;
    }
}
