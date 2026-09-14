package com.unihub.workshop.workers.maintenance.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PostgresTtlCleanupAdapterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PostgresTtlCleanupAdapter adapter;

    @Test
    void purgeExpiredRows_executesBoundedSubqueryDelete() {
        String expectedSql =
            "DELETE FROM idempotency_keys WHERE id IN (SELECT id FROM idempotency_keys WHERE expires_at < NOW() LIMIT 500)";
        when(jdbcTemplate.update(eq(expectedSql))).thenReturn(42);

        int result = adapter.purgeExpiredRows("idempotency_keys", 500);

        assertThat(result).isEqualTo(42);
        verify(jdbcTemplate).update(eq(expectedSql));
    }

    @Test
    void purgeExpiredRows_withDisallowedTable_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> adapter.purgeExpiredRows("users", 500))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Disallowed table for TTL cleanup: users");

        assertThatThrownBy(() ->
            adapter.purgeExpiredRows(
                "idempotency_keys; DROP TABLE users; --",
                500
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Disallowed table for TTL cleanup");
    }

    @Test
    void purgeExpiredRows_withNonPositiveBatchSize_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
            adapter.purgeExpiredRows("idempotency_keys", 0)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchSize must be positive: 0");

        assertThatThrownBy(() ->
            adapter.purgeExpiredRows("idempotency_keys", -10)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchSize must be positive: -10");
    }
}
