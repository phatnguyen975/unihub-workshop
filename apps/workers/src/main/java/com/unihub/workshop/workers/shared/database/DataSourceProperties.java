package com.unihub.workshop.workers.shared.database;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("datasource")
public record DataSourceProperties(DataSourceConfig primary, DataSourceConfig replica) {
    public record DataSourceConfig(
            String url,
            String username,
            String password,
            String driverClassName,
            Hikari hikari) {
    }

    public record Hikari(Integer maximumPoolSize) {
    }
}
