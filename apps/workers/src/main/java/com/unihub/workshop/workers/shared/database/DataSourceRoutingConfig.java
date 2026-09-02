package com.unihub.workshop.workers.shared.database;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceRoutingConfig {

    @Bean
    public DataSource primaryDataSource(DataSourceProperties properties) {
        return createHikariDataSource(properties.primary());
    }

    @Bean
    public DataSource replicaDataSource(DataSourceProperties properties) {
        return createHikariDataSource(properties.replica());
    }

    @Bean
    @Primary
    public DataSource routingDataSource(DataSource primaryDataSource, DataSource replicaDataSource) {
        TransactionAwareRoutingDataSource routingDataSource = new TransactionAwareRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("primary", primaryDataSource);
        targetDataSources.put("replica", replicaDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);
        routingDataSource.afterPropertiesSet();

        return routingDataSource;
    }

    private HikariDataSource createHikariDataSource(DataSourceProperties.DataSourceConfig config) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.url());
        dataSource.setUsername(config.username());
        dataSource.setPassword(config.password());
        dataSource.setDriverClassName(config.driverClassName());
        if (config.hikari() != null && config.hikari().maximumPoolSize() != null) {
            dataSource.setMaximumPoolSize(config.hikari().maximumPoolSize());
        }
        return dataSource;
    }
}
