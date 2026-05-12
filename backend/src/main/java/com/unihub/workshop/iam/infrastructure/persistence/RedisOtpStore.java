package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.domain.repository.OtpStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

@Component
public class RedisOtpStore implements OtpStore {

    private static final String PREFIX = "otp:";
    private static final String COOLDOWN_PREFIX = "otp:cooldown:";
    private static final String ATTEMPTS_PREFIX = "otp:attempts:";

    private final StringRedisTemplate stringRedisTemplate;
    private final Map<String, ExpiringValue<String>> otpCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> cooldownCache = new ConcurrentHashMap<>();
    private final Map<String, AttemptCounter> attemptsCache = new ConcurrentHashMap<>();

    public RedisOtpStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void saveOtp(String email, String otp, Duration ttl) {
        ValueOperations<String, String> ops = ops();
        if (ops != null) {
            ops.set(key(email), otp, ttl);
            return;
        }
        otpCache.put(key(email), new ExpiringValue<>(otp, expiresAt(ttl)));
    }

    @Override
    public String getOtp(String email) {
        ValueOperations<String, String> ops = ops();
        if (ops != null) {
            return ops.get(key(email));
        }
        return getCachedOtp(key(email));
    }

    @Override
    public void deleteOtp(String email) {
        otpCache.remove(key(email));
        if (stringRedisTemplate != null) {
            stringRedisTemplate.delete(key(email));
        }
    }

    @Override
    public boolean isCooldownActive(String email) {
        String cooldownKey = cooldownKey(email);
        ValueOperations<String, String> ops = ops();
        if (ops != null) {
            return ops.get(cooldownKey) != null;
        }
        Instant expiry = cooldownCache.get(cooldownKey);
        if (expiry == null) {
            return false;
        }
        if (expiry.isAfter(Instant.now())) {
            return true;
        }
        cooldownCache.remove(cooldownKey);
        return false;
    }

    @Override
    public void markCooldown(String email, Duration ttl) {
        String cooldownKey = cooldownKey(email);
        ValueOperations<String, String> ops = ops();
        if (ops != null) {
            ops.set(cooldownKey, "1", ttl);
            return;
        }
        cooldownCache.put(cooldownKey, expiresAt(ttl));
    }

    @Override
    public int incrementFailedAttempts(String email, Duration ttl) {
        String attemptsKey = attemptsKey(email);
        ValueOperations<String, String> ops = ops();
        if (ops != null) {
            Long count = ops.increment(attemptsKey);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(attemptsKey, ttl);
            }
            return count == null ? 0 : count.intValue();
        }
        AttemptCounter counter = attemptsCache.get(attemptsKey);
        if (counter == null || counter.isExpired()) {
            counter = new AttemptCounter(0, expiresAt(ttl));
        }
        counter.increment(ttl);
        attemptsCache.put(attemptsKey, counter);
        return counter.count();
    }

    @Override
    public void resetFailedAttempts(String email) {
        String attemptsKey = attemptsKey(email);
        attemptsCache.remove(attemptsKey);
        if (stringRedisTemplate != null) {
            stringRedisTemplate.delete(attemptsKey);
        }
    }

    private String key(String email) {
        return PREFIX + email.toLowerCase();
    }

    private String cooldownKey(String email) {
        return COOLDOWN_PREFIX + email.toLowerCase();
    }

    private String attemptsKey(String email) {
        return ATTEMPTS_PREFIX + email.toLowerCase();
    }

    private ValueOperations<String, String> ops() {
        return stringRedisTemplate == null ? null : stringRedisTemplate.opsForValue();
    }

    private Instant expiresAt(Duration ttl) {
        return Instant.now().plus(ttl);
    }

    private String getCachedOtp(String key) {
        ExpiringValue<String> cached = otpCache.get(key);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        otpCache.remove(key);
        return null;
    }

    private record ExpiringValue<T>(T value, Instant expiresAt) {
    }

    private static final class AttemptCounter {
        private int count;
        private Instant expiresAt;

        private AttemptCounter(int count, Instant expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }

        private void increment(Duration ttl) {
            if (isExpired()) {
                count = 0;
            }
            count += 1;
            expiresAt = Instant.now().plus(ttl);
        }

        private boolean isExpired() {
            return expiresAt == null || expiresAt.isBefore(Instant.now());
        }

        private int count() {
            return count;
        }
    }
}
