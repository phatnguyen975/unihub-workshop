package com.unihub.workshop.workers.maintenance.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
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
}
