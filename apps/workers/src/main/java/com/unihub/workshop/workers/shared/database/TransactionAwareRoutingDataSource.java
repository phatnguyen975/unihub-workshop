package com.unihub.workshop.workers.shared.database;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionAwareRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        if (TransactionSynchronizationManager.isSynchronizationActive() &&
                TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return "replica";
        }
        return "primary";
    }
}
