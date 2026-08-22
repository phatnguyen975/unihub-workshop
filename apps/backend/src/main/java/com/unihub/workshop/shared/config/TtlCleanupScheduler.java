package com.unihub.workshop.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Configuration
@EnableScheduling
public class TtlCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TtlCleanupScheduler.class);
    private static final int BATCH_SIZE = 500;

    private static final List<String> TTL_TABLES = List.of(
            "idempotency_keys",
            "refresh_tokens",
            "password_reset_tokens");

    private final JdbcTemplate jdbcTemplate;

    public TtlCleanupScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void cleanExpiredTokens() {
        for (String table : TTL_TABLES) {
            int totalDeleted = 0;
            int deletedInBatch;
            String query = "DELETE FROM " + table + " WHERE id IN (SELECT id FROM " + table
                    + " WHERE expires_at < NOW() LIMIT " + BATCH_SIZE + ")";

            do {
                deletedInBatch = jdbcTemplate.update(query);
                totalDeleted += deletedInBatch;
            } while (deletedInBatch == BATCH_SIZE);

            if (totalDeleted > 0 || log.isDebugEnabled()) {
                log.info("TTL cleanup: deleted {} rows from {}", totalDeleted, table);
            } else {
                log.info("TTL cleanup: deleted 0 rows from {}", table);
            }
        }
    }
}
