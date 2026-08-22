package com.unihub.workshop.shared.config.datasource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRoutingConfigTest {

    @Test
    void determineCurrentLookupKey_whenReadOnlyTransaction_returnsReplica() throws Exception {
        TransactionAwareRoutingDataSource routingDataSource = new TransactionAwareRoutingDataSource();

        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

            Method method = AbstractRoutingDataSource.class.getDeclaredMethod("determineCurrentLookupKey");
            method.setAccessible(true);
            Object key = method.invoke(routingDataSource);

            assertThat(key).isEqualTo("replica");
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void determineCurrentLookupKey_whenWriteTransaction_returnsPrimary() throws Exception {
        TransactionAwareRoutingDataSource routingDataSource = new TransactionAwareRoutingDataSource();

        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

            Method method = AbstractRoutingDataSource.class.getDeclaredMethod("determineCurrentLookupKey");
            method.setAccessible(true);
            Object key = method.invoke(routingDataSource);

            assertThat(key).isEqualTo("primary");
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void determineCurrentLookupKey_whenNoTransaction_returnsPrimary() throws Exception {
        TransactionAwareRoutingDataSource routingDataSource = new TransactionAwareRoutingDataSource();

        Method method = AbstractRoutingDataSource.class.getDeclaredMethod("determineCurrentLookupKey");
        method.setAccessible(true);
        Object key = method.invoke(routingDataSource);

        assertThat(key).isEqualTo("primary");
    }
}
