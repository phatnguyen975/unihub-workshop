package com.unihub.workshop.workshops.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisWorkshopLockAdapter Unit Tests")
class RedisWorkshopLockAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisWorkshopLockAdapter lockAdapter;

    @BeforeEach
    void setUp() {
        lockAdapter = new RedisWorkshopLockAdapter(redisTemplate);
    }

    @Nested
    @DisplayName("tryLock")
    class TryLockTests {

        @Test
        @DisplayName(
            "Returns true when key was absent and lock successfully acquired"
        )
        void tryLock_acquired_returnsTrue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(
                valueOperations.setIfAbsent(
                    eq("lock:test"),
                    eq("1"),
                    eq(Duration.ofSeconds(240))
                )
            ).thenReturn(Boolean.TRUE);

            boolean acquired = lockAdapter.tryLock(
                "lock:test",
                Duration.ofSeconds(240)
            );

            assertThat(acquired).isTrue();
            verify(valueOperations).setIfAbsent(
                "lock:test",
                "1",
                Duration.ofSeconds(240)
            );
        }

        @Test
        @DisplayName("Returns false when key already exists")
        void tryLock_alreadyHeld_returnsFalse() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(
                valueOperations.setIfAbsent(
                    eq("lock:test"),
                    eq("1"),
                    eq(Duration.ofSeconds(240))
                )
            ).thenReturn(Boolean.FALSE);

            boolean acquired = lockAdapter.tryLock(
                "lock:test",
                Duration.ofSeconds(240)
            );

            assertThat(acquired).isFalse();
        }

        @Test
        @DisplayName("Returns false when redis template throws an exception")
        void tryLock_exception_returnsFalseWithoutPropagating() {
            when(redisTemplate.opsForValue()).thenThrow(
                new RuntimeException("Redis connection refused")
            );

            boolean acquired = lockAdapter.tryLock(
                "lock:test",
                Duration.ofSeconds(240)
            );

            assertThat(acquired).isFalse();
        }
    }

    @Nested
    @DisplayName("releaseLock")
    class ReleaseLockTests {

        @Test
        @DisplayName("Deletes lock key on release")
        void releaseLock_deletesKey() {
            when(redisTemplate.delete("lock:test")).thenReturn(Boolean.TRUE);

            lockAdapter.releaseLock("lock:test");

            verify(redisTemplate).delete("lock:test");
        }

        @Test
        @DisplayName("Swallows exception when delete fails")
        void releaseLock_exception_swallowsException() {
            doThrow(new RuntimeException("Redis timeout"))
                .when(redisTemplate)
                .delete("lock:test");

            lockAdapter.releaseLock("lock:test");

            verify(redisTemplate).delete("lock:test");
        }
    }
}
