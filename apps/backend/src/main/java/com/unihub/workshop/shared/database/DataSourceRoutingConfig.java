package com.unihub.workshop.shared.database;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceRoutingConfig {

    public static final String DEFAULT_PRIMARY_POOL_NAME = "primary-pool";
    public static final String DEFAULT_REPLICA_POOL_NAME = "replica-pool";

    private final DataSourceProperties properties;

    public DataSourceRoutingConfig(DataSourceProperties properties) {
        this.properties = Objects.requireNonNull(
            properties,
            "DataSourceProperties must not be null"
        );
    }

    @Bean
    public DataSource primaryDataSource() {
        Objects.requireNonNull(
            properties.primary(),
            "datasource.primary configuration must not be null"
        );
        return createHikariDataSource(
            properties.primary(),
            DEFAULT_PRIMARY_POOL_NAME
        );
    }

    @Bean
    public DataSource replicaDataSource() {
        Objects.requireNonNull(
            properties.replica(),
            "datasource.replica configuration must not be null"
        );
        return createHikariDataSource(
            properties.replica(),
            DEFAULT_REPLICA_POOL_NAME
        );
    }

    @Bean
    @Primary
    public DataSource routingDataSource(
        @Qualifier("primaryDataSource") DataSource primaryDataSource,
        @Qualifier("replicaDataSource") DataSource replicaDataSource
    ) {
        return new TransactionAwareRoutingDataSource(
            primaryDataSource,
            replicaDataSource
        );
    }

    private HikariDataSource createHikariDataSource(
        DataSourceProperties.DataSourceConfig config,
        String defaultPoolName
    ) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName(defaultPoolName);
        dataSource.setJdbcUrl(config.url());
        dataSource.setUsername(config.username());
        dataSource.setPassword(config.password());
        if (
            config.driverClassName() != null &&
            !config.driverClassName().isBlank()
        ) {
            dataSource.setDriverClassName(config.driverClassName());
        }

        if (config.hikari() != null) {
            DataSourceProperties.Hikari hikari = config.hikari();
            if (hikari.poolName() != null && !hikari.poolName().isBlank()) {
                dataSource.setPoolName(hikari.poolName());
            }
            if (hikari.maximumPoolSize() != null) {
                dataSource.setMaximumPoolSize(hikari.maximumPoolSize());
            }
            if (hikari.minimumIdle() != null) {
                dataSource.setMinimumIdle(hikari.minimumIdle());
            }
            if (hikari.connectionTimeoutMs() != null) {
                dataSource.setConnectionTimeout(hikari.connectionTimeoutMs());
            }
            if (hikari.idleTimeoutMs() != null) {
                dataSource.setIdleTimeout(hikari.idleTimeoutMs());
            }
            if (hikari.maxLifetimeMs() != null) {
                dataSource.setMaxLifetime(hikari.maxLifetimeMs());
            }
        }
        return dataSource;
    }
}
