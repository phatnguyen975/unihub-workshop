package com.unihub.workshop.workers.maintenance.core.usecases;

import com.unihub.workshop.workers.maintenance.core.ports.in.RunTtlCleanup;
import com.unihub.workshop.workers.maintenance.core.ports.out.TtlCleanupPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TtlCleanupService implements RunTtlCleanup {

    private static final Logger log = LoggerFactory.getLogger(TtlCleanupService.class);
    private static final int BATCH_SIZE = 500;

    private static final List<String> TTL_TABLES = List.of(
            "idempotency_keys",
            "refresh_tokens",
            "user_verification_tokens");

    private final TtlCleanupPort ttlCleanupPort;

    @Override
    @Transactional
    public void cleanExpiredTokens() {
        for (String table : TTL_TABLES) {
            int totalDeleted = 0;
            int deletedInBatch;
            do {
                deletedInBatch = ttlCleanupPort.purgeExpiredRows(table, BATCH_SIZE);
                totalDeleted += deletedInBatch;
            } while (deletedInBatch == BATCH_SIZE);

            log.info("TTL cleanup: deleted {} rows from {}", totalDeleted, table);
        }
    }
}
