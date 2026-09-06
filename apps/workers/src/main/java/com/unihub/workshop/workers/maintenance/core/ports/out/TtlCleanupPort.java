package com.unihub.workshop.workers.maintenance.core.ports.out;

public interface TtlCleanupPort {
    int purgeExpiredRows(String tableName, int batchSize);
}
