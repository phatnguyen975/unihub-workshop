package com.unihub.workshop.workers.maintenance.outbound.persistence;

import com.unihub.workshop.workers.maintenance.core.ports.out.TtlCleanupPort;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresTtlCleanupAdapter implements TtlCleanupPort {

    private static final Set<String> ALLOWED_TABLES = Set.of(
        "idempotency_keys",
        "refresh_tokens",
        "user_verification_tokens"
    );

    private static final String DELETE_EXPIRED_SQL_TEMPLATE =
        "DELETE FROM %s WHERE id IN (SELECT id FROM %s WHERE expires_at < NOW() LIMIT %d)";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int purgeExpiredRows(String tableName, int batchSize) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            throw new IllegalArgumentException(
                "Disallowed table for TTL cleanup: " + tableName
            );
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "batchSize must be positive: " + batchSize
            );
        }

        String sql = DELETE_EXPIRED_SQL_TEMPLATE.formatted(
            tableName,
            tableName,
            batchSize
        );
        return jdbcTemplate.update(sql);
    }
}
