package com.unihub.workshop.shared.database;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("datasource")
public record DataSourceProperties(
    DataSourceConfig primary,
    DataSourceConfig replica
) {
    public record DataSourceConfig(
        String url,
        String username,
        String password,
        String driverClassName,
        Hikari hikari
    ) {}

    public record Hikari(
        Integer maximumPoolSize,
        Integer minimumIdle,
        Long connectionTimeoutMs,
        Long idleTimeoutMs,
        Long maxLifetimeMs,
        String poolName
    ) {}
}
