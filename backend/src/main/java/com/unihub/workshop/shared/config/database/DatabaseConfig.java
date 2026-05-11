package com.unihub.workshop.shared.config.database;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

@Configuration
public class DatabaseConfig {

    @Value("${NEON_PRIMARY_URL}")
    private String primaryUrl;

    @Value("${NEON_REPLICA_URL}")
    private String replicaUrl;

    @Value("${DB_USERNAME}")
    private String dbUsername;

    @Value("${DB_PASSWORD}")
    private String dbPassword;

    @Bean(name = "primaryDataSource")
    public HikariDataSource primaryDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(primaryUrl);
        dataSource.setUsername(dbUsername);
        dataSource.setPassword(dbPassword);
        return dataSource;
    }

    @Bean(name = "replicaDataSource")
    public HikariDataSource replicaDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(replicaUrl);
        dataSource.setUsername(dbUsername);
        dataSource.setPassword(dbPassword);
        dataSource.setReadOnly(true);
        return dataSource;
    }

    @Bean(name = "routingDataSource")
    public DataSource routingDataSource(
            HikariDataSource primaryDataSource,
            HikariDataSource replicaDataSource) {
        ReplicaRoutingDataSource routingDataSource = new ReplicaRoutingDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.PRIMARY, primaryDataSource);
        targetDataSources.put(DataSourceType.REPLICA, replicaDataSource);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Bean(name = "dataSource")
    @Primary
    public DataSource dataSource(DataSource routingDataSource) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
