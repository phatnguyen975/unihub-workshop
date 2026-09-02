package com.unihub.workshop.shared.database;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRoutingConfigTest {

    /**
     * Avoids fragile reflection into Spring internals.
     * Exposes the protected method through a typed subclass — if the method
     * signature changes in a future Spring version, this fails at compile time
     * with a useful error rather than at runtime with NoSuchMethodException.
     */
    private static class TestableRoutingDataSource extends TransactionAwareRoutingDataSource {
        public Object currentLookupKey() {
            return determineCurrentLookupKey();
        }
    }

    @Test
    void determineCurrentLookupKey_whenReadOnlyTransaction_returnsReplica() {
        TestableRoutingDataSource routingDataSource = new TestableRoutingDataSource();

        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

            assertThat(routingDataSource.currentLookupKey()).isEqualTo("replica");
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void determineCurrentLookupKey_whenWriteTransaction_returnsPrimary() {
        TestableRoutingDataSource routingDataSource = new TestableRoutingDataSource();

        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

            assertThat(routingDataSource.currentLookupKey()).isEqualTo("primary");
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void determineCurrentLookupKey_whenNoTransaction_returnsPrimary() {
        TestableRoutingDataSource routingDataSource = new TestableRoutingDataSource();

        assertThat(routingDataSource.currentLookupKey()).isEqualTo("primary");
    }
}
