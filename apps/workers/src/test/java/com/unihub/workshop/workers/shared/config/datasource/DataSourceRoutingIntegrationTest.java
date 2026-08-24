package com.unihub.workshop.workers.shared.config.datasource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "SPRING_DATASOURCE_PRIMARY_URL=jdbc:h2:mem:primary;DB_CLOSE_DELAY=-1",
        "SPRING_DATASOURCE_PRIMARY_USERNAME=sa",
        "SPRING_DATASOURCE_PRIMARY_PASSWORD=",
        "SPRING_DATASOURCE_REPLICA_URL=jdbc:h2:mem:replica;DB_CLOSE_DELAY=-1",
        "SPRING_DATASOURCE_REPLICA_USERNAME=sa",
        "SPRING_DATASOURCE_REPLICA_PASSWORD=",
        "datasource.primary.driver-class-name=org.h2.Driver",
        "datasource.replica.driver-class-name=org.h2.Driver"
})
class DataSourceRoutingIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DataSource dataSource;

    @Test
    void routingDataSource_isPrimaryDataSourceBean() {
        assertThat(dataSource).isInstanceOf(TransactionAwareRoutingDataSource.class);

        DataSource primaryBean = context.getBean(DataSource.class);
        assertThat(primaryBean).isSameAs(dataSource);
    }
}
