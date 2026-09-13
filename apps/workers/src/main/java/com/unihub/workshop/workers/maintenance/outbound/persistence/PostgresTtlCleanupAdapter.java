package com.unihub.workshop.workers.maintenance.outbound.persistence;

import com.unihub.workshop.workers.maintenance.core.ports.out.TtlCleanupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostgresTtlCleanupAdapter implements TtlCleanupPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int purgeExpiredRows(String tableName, int batchSize) {
        String sql =
            "DELETE FROM " +
            tableName +
            " WHERE id IN (SELECT id FROM " +
            tableName +
            " WHERE expires_at < NOW() LIMIT " +
            batchSize +
            ")";
        return jdbcTemplate.update(sql);
    }
}
