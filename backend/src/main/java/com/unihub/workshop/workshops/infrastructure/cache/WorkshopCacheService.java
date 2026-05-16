package com.unihub.workshop.workshops.infrastructure.cache;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkshopCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PUBLIC_LIST_KEY_PREFIX = "workshops:public:list:";
    private static final String PUBLIC_DETAIL_KEY_PREFIX = "workshops:public:detail:";
    private static final String MANAGEMENT_DETAIL_KEY_PREFIX = "workshops:management:detail:";

    private static final long PUBLIC_LIST_TTL_MINUTES = 5;
    private static final long PUBLIC_DETAIL_TTL_MINUTES = 15;
    private static final long MANAGEMENT_DETAIL_TTL_MINUTES = 10;
    private static final long STAFF_AVAILABLE_TTL_MINUTES = 5;

    public void setPublicListCache(String key, Object value) {
        redisTemplate.opsForValue().set(
                PUBLIC_LIST_KEY_PREFIX + key,
                value,
                PUBLIC_LIST_TTL_MINUTES,
                TimeUnit.MINUTES);
    }

    public Object getPublicListCache(String key) {
        return redisTemplate.opsForValue().get(PUBLIC_LIST_KEY_PREFIX + key);
    }

    public void setPublicDetailCache(String workshopId, Object value) {
        redisTemplate.opsForValue().set(
                PUBLIC_DETAIL_KEY_PREFIX + workshopId,
                value,
                PUBLIC_DETAIL_TTL_MINUTES,
                TimeUnit.MINUTES);
    }

    public Object getPublicDetailCache(String workshopId) {
        return redisTemplate.opsForValue().get(PUBLIC_DETAIL_KEY_PREFIX + workshopId);
    }

    public void setManagementDetailCache(String workshopId, Object value) {
        redisTemplate.opsForValue().set(
                MANAGEMENT_DETAIL_KEY_PREFIX + workshopId,
                value,
                MANAGEMENT_DETAIL_TTL_MINUTES,
                TimeUnit.MINUTES);
    }

    public Object getManagementDetailCache(String workshopId) {
        return redisTemplate.opsForValue().get(MANAGEMENT_DETAIL_KEY_PREFIX + workshopId);
    }

    public void setStaffAvailableCache(String key, Object value) {
        redisTemplate.opsForValue().set(
                "workshops:management:staff:available:" + key,
                value,
                STAFF_AVAILABLE_TTL_MINUTES,
                TimeUnit.MINUTES);
    }

    public Object getStaffAvailableCache(String key) {
        return redisTemplate.opsForValue().get("workshops:management:staff:available:" + key);
    }

    public void evictPublicListCachePattern() {
        evictKeyPattern(PUBLIC_LIST_KEY_PREFIX + "*");
    }

    public void evictPublicDetailCache(String workshopId) {
        redisTemplate.delete(PUBLIC_DETAIL_KEY_PREFIX + workshopId);
    }

    public void evictManagementDetailCache(String workshopId) {
        redisTemplate.delete(MANAGEMENT_DETAIL_KEY_PREFIX + workshopId);
    }

    public void evictAllDetailCaches(String workshopId) {
        evictPublicDetailCache(workshopId);
        evictManagementDetailCache(workshopId);
    }

    public void evictStaffAvailablePattern(String workshopId) {
        evictKeyPattern("workshops:management:staff:available:" + workshopId + ":*");
    }

    private void evictKeyPattern(String pattern) {
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
