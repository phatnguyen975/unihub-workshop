package com.unihub.workshop.workers.shared.config.datasource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRoutingIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DataSourceRoutingConfig.class)
            .withPropertyValues(
                    "datasource.primary.url=jdbc:h2:mem:primary;DB_CLOSE_DELAY=-1",
                    "datasource.primary.username=sa",
                    "datasource.primary.password=",
                    "datasource.primary.driver-class-name=org.h2.Driver",
                    "datasource.replica.url=jdbc:h2:mem:replica;DB_CLOSE_DELAY=-1",
                    "datasource.replica.username=sa",
                    "datasource.replica.password=",
                    "datasource.replica.driver-class-name=org.h2.Driver");

    @Test
    void routingDataSource_isPrimaryDataSourceBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DataSourceRoutingConfig.class);
            DataSource dataSource = context.getBean(DataSource.class);
            assertThat(dataSource).isInstanceOf(TransactionAwareRoutingDataSource.class);
        });
    }
}
