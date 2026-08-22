package com.unihub.workshop.shared.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TtlCleanupSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TtlCleanupScheduler scheduler;

    @Test
    void cleanExpiredTokens_when1200ExpiredRows_callsUpdateThreeTimes() {
        String query1 = "DELETE FROM idempotency_keys WHERE id IN (SELECT id FROM idempotency_keys WHERE expires_at < NOW() LIMIT 500)";
        String query2 = "DELETE FROM refresh_tokens WHERE id IN (SELECT id FROM refresh_tokens WHERE expires_at < NOW() LIMIT 500)";
        String query3 = "DELETE FROM password_reset_tokens WHERE id IN (SELECT id FROM password_reset_tokens WHERE expires_at < NOW() LIMIT 500)";

        // Idempotency keys has 1200 items (500 + 500 + 200 + 0)
        when(jdbcTemplate.update(eq(query1))).thenReturn(500, 500, 200);

        // Others have 0
        when(jdbcTemplate.update(eq(query2))).thenReturn(0);
        when(jdbcTemplate.update(eq(query3))).thenReturn(0);

        scheduler.cleanExpiredTokens();

        verify(jdbcTemplate, times(3)).update(eq(query1));
        verify(jdbcTemplate, times(1)).update(eq(query2));
        verify(jdbcTemplate, times(1)).update(eq(query3));
    }

    @Test
    void cleanExpiredTokens_when0ExpiredRows_callsUpdateOncePerTable() {
        String query1 = "DELETE FROM idempotency_keys WHERE id IN (SELECT id FROM idempotency_keys WHERE expires_at < NOW() LIMIT 500)";
        String query2 = "DELETE FROM refresh_tokens WHERE id IN (SELECT id FROM refresh_tokens WHERE expires_at < NOW() LIMIT 500)";
        String query3 = "DELETE FROM password_reset_tokens WHERE id IN (SELECT id FROM password_reset_tokens WHERE expires_at < NOW() LIMIT 500)";

        when(jdbcTemplate.update(eq(query1))).thenReturn(0);
        when(jdbcTemplate.update(eq(query2))).thenReturn(0);
        when(jdbcTemplate.update(eq(query3))).thenReturn(0);

        scheduler.cleanExpiredTokens();

        verify(jdbcTemplate, times(1)).update(eq(query1));
        verify(jdbcTemplate, times(1)).update(eq(query2));
        verify(jdbcTemplate, times(1)).update(eq(query3));
    }

    @Test
    void cleanExpiredTokens_isAnnotatedWithTransactional() throws NoSuchMethodException {
        Method method = TtlCleanupScheduler.class.getDeclaredMethod("cleanExpiredTokens");
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        // Should use default readOnly = false
        assertThat(transactional.readOnly()).isFalse();
    }
}
