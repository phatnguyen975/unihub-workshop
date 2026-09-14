package com.unihub.workshop.workers.shared.database;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionAwareRoutingDataSource
    extends AbstractRoutingDataSource
{

    public static final String DATASOURCE_PRIMARY = "primary";
    public static final String DATASOURCE_REPLICA = "replica";

    public TransactionAwareRoutingDataSource() {}

    public TransactionAwareRoutingDataSource(
        DataSource primaryDataSource,
        DataSource replicaDataSource
    ) {
        Objects.requireNonNull(
            primaryDataSource,
            "primaryDataSource must not be null"
        );
        Objects.requireNonNull(
            replicaDataSource,
            "replicaDataSource must not be null"
        );

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DATASOURCE_PRIMARY, primaryDataSource);
        targetDataSources.put(DATASOURCE_REPLICA, replicaDataSource);

        setTargetDataSources(targetDataSources);
        setDefaultTargetDataSource(primaryDataSource);
        afterPropertiesSet();
    }

    @Override
    public Object determineCurrentLookupKey() {
        if (
            TransactionSynchronizationManager.isSynchronizationActive() &&
            TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        ) {
            return DATASOURCE_REPLICA;
        }
        return DATASOURCE_PRIMARY;
    }
}
