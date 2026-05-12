package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.domain.repository.OtpStore;
import com.unihub.workshop.shared.exception.AppException;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisOtpStore implements OtpStore {

    private static final String PREFIX = "otp:";
    private static final String COOLDOWN_PREFIX = "otp:cooldown:";
    private static final String ATTEMPTS_PREFIX = "otp:attempts:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisOtpStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void saveOtp(String email, String otp, Duration ttl) {
        runRedis(() -> stringRedisTemplate.opsForValue().set(key(email), otp, ttl));
    }

    @Override
    public String getOtp(String email) {
        return callRedis(() -> stringRedisTemplate.opsForValue().get(key(email)));
    }

    @Override
    public void deleteOtp(String email) {
        runRedis(() -> stringRedisTemplate.delete(key(email)));
    }

    @Override
    public boolean isCooldownActive(String email) {
        String cooldownKey = cooldownKey(email);
        return Boolean.TRUE.equals(callRedis(() -> stringRedisTemplate.hasKey(cooldownKey)));
    }

    @Override
    public void markCooldown(String email, Duration ttl) {
        runRedis(() -> stringRedisTemplate.opsForValue().set(cooldownKey(email), "1", ttl));
    }

    @Override
    public int incrementFailedAttempts(String email, Duration ttl) {
        String attemptsKey = attemptsKey(email);
        return callRedis(() -> {
            Long count = stringRedisTemplate.opsForValue().increment(attemptsKey);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(attemptsKey, ttl);
            }
            return count == null ? 0 : count.intValue();
        });
    }

    @Override
    public void resetFailedAttempts(String email) {
        runRedis(() -> stringRedisTemplate.delete(attemptsKey(email)));
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

    private void runRedis(RedisRunnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "OTP infrastructure unavailable", "otp_infra");
        }
    }

    private <T> T callRedis(RedisSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "OTP infrastructure unavailable", "otp_infra");
        }
    }

    @FunctionalInterface
    private interface RedisRunnable {
        void run();
    }

    @FunctionalInterface
    private interface RedisSupplier<T> {
        T get();
    }
}
