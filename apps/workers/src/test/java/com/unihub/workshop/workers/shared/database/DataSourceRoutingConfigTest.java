package com.unihub.workshop.workers.shared.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DataSourceRoutingConfigTest {

    private final TransactionAwareRoutingDataSource routingDataSource =
        new TransactionAwareRoutingDataSource();

    @Test
    void determineCurrentLookupKey_whenReadOnlyTransaction_returnsReplica() {
        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(
                true
            );

            assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo(
                TransactionAwareRoutingDataSource.DATASOURCE_REPLICA
            );
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void determineCurrentLookupKey_whenWriteTransaction_returnsPrimary() {
        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(
                false
            );

            assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo(
                TransactionAwareRoutingDataSource.DATASOURCE_PRIMARY
            );
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void determineCurrentLookupKey_whenNoTransaction_returnsPrimary() {
        assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo(
            TransactionAwareRoutingDataSource.DATASOURCE_PRIMARY
        );
    }

    @Test
    void configConstructor_throwsNullPointerExceptionWhenPropertiesNull() {
        assertThatThrownBy(() -> new DataSourceRoutingConfig(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("DataSourceProperties must not be null");
    }

    @Test
    void primaryDataSource_createsHikariDataSourceWithDefaultPoolNameAndSettings() {
        DataSourceProperties.Hikari hikari = new DataSourceProperties.Hikari(
            15,
            3,
            20000L,
            300000L,
            900000L,
            null
        );
        DataSourceProperties.DataSourceConfig config =
            new DataSourceProperties.DataSourceConfig(
                "jdbc:h2:mem:workers_primary",
                "sa",
                "secret",
                "org.h2.Driver",
                hikari
            );
        DataSourceProperties properties = new DataSourceProperties(
            config,
            null
        );
        DataSourceRoutingConfig routingConfig = new DataSourceRoutingConfig(
            properties
        );

        try (
            HikariDataSource hikariDs =
                (HikariDataSource) routingConfig.primaryDataSource()
        ) {
            assertThat(hikariDs.getPoolName()).isEqualTo(
                DataSourceRoutingConfig.DEFAULT_PRIMARY_POOL_NAME
            );
            assertThat(hikariDs.getJdbcUrl()).isEqualTo(
                "jdbc:h2:mem:workers_primary"
            );
            assertThat(hikariDs.getUsername()).isEqualTo("sa");
            assertThat(hikariDs.getPassword()).isEqualTo("secret");
            assertThat(hikariDs.getDriverClassName()).isEqualTo(
                "org.h2.Driver"
            );
            assertThat(hikariDs.getMaximumPoolSize()).isEqualTo(15);
            assertThat(hikariDs.getMinimumIdle()).isEqualTo(3);
            assertThat(hikariDs.getConnectionTimeout()).isEqualTo(20000L);
            assertThat(hikariDs.getIdleTimeout()).isEqualTo(300000L);
            assertThat(hikariDs.getMaxLifetime()).isEqualTo(900000L);
        }
    }

    @Test
    void replicaDataSource_createsHikariDataSourceWithCustomPoolNameWhenProvided() {
        DataSourceProperties.Hikari hikari = new DataSourceProperties.Hikari(
            8,
            2,
            10000L,
            120000L,
            600000L,
            "custom-workers-replica-pool"
        );
        DataSourceProperties.DataSourceConfig config =
            new DataSourceProperties.DataSourceConfig(
                "jdbc:h2:mem:workers_replica",
                "sa",
                "secret",
                "org.h2.Driver",
                hikari
            );
        DataSourceProperties properties = new DataSourceProperties(
            null,
            config
        );
        DataSourceRoutingConfig routingConfig = new DataSourceRoutingConfig(
            properties
        );

        try (
            HikariDataSource hikariDs =
                (HikariDataSource) routingConfig.replicaDataSource()
        ) {
            assertThat(hikariDs.getPoolName()).isEqualTo(
                "custom-workers-replica-pool"
            );
            assertThat(hikariDs.getMaximumPoolSize()).isEqualTo(8);
            assertThat(hikariDs.getMinimumIdle()).isEqualTo(2);
        }
    }

    @Test
    void primaryDataSource_throwsNullPointerExceptionWhenConfigMissing() {
        DataSourceProperties properties = new DataSourceProperties(null, null);
        DataSourceRoutingConfig routingConfig = new DataSourceRoutingConfig(
            properties
        );

        assertThatThrownBy(routingConfig::primaryDataSource)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("datasource.primary");
    }

    @Test
    void replicaDataSource_throwsNullPointerExceptionWhenConfigMissing() {
        DataSourceProperties properties = new DataSourceProperties(null, null);
        DataSourceRoutingConfig routingConfig = new DataSourceRoutingConfig(
            properties
        );

        assertThatThrownBy(routingConfig::replicaDataSource)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("datasource.replica");
    }

    @Test
    void routingDataSource_configuresTargetDataSourcesAndDefault() {
        DataSourceProperties properties = new DataSourceProperties(null, null);
        DataSourceRoutingConfig routingConfig = new DataSourceRoutingConfig(
            properties
        );

        try (
            HikariDataSource primary = new HikariDataSource();
            HikariDataSource replica = new HikariDataSource()
        ) {
            primary.setJdbcUrl("jdbc:h2:mem:wp;DB_CLOSE_DELAY=-1");
            replica.setJdbcUrl("jdbc:h2:mem:wr;DB_CLOSE_DELAY=-1");

            DataSource routing = routingConfig.routingDataSource(
                primary,
                replica
            );

            assertThat(routing).isInstanceOf(
                TransactionAwareRoutingDataSource.class
            );
        }
    }

    @Test
    void routingDataSourceConstructor_withNulls_throwsNullPointerException() {
        try (HikariDataSource ds = new HikariDataSource()) {
            ds.setJdbcUrl("jdbc:h2:mem:wds;DB_CLOSE_DELAY=-1");

            assertThatThrownBy(() ->
                new TransactionAwareRoutingDataSource(null, ds)
            )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("primaryDataSource must not be null");

            assertThatThrownBy(() ->
                new TransactionAwareRoutingDataSource(ds, null)
            )
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("replicaDataSource must not be null");
        }
    }
}
