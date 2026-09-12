package com.unihub.workshop.shared.database;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
        "spring.flyway.enabled=false",
        "SPRING_DATASOURCE_PRIMARY_URL=jdbc:h2:mem:primary;DB_CLOSE_DELAY=-1",
        "SPRING_DATASOURCE_PRIMARY_USERNAME=sa",
        "SPRING_DATASOURCE_PRIMARY_PASSWORD=",
        "SPRING_DATASOURCE_REPLICA_URL=jdbc:h2:mem:replica;DB_CLOSE_DELAY=-1",
        "SPRING_DATASOURCE_REPLICA_USERNAME=sa",
        "SPRING_DATASOURCE_REPLICA_PASSWORD=",
        "datasource.primary.driver-class-name=org.h2.Driver",
        "datasource.replica.driver-class-name=org.h2.Driver",
    }
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class DataSourceRoutingIntegrationTest {

    private final ApplicationContext context;
    private final DataSource dataSource;

    @Test
    void routingDataSource_isPrimaryDataSourceBean() {
        assertThat(dataSource).isInstanceOf(
            TransactionAwareRoutingDataSource.class
        );

        DataSource primaryBean = context.getBean(DataSource.class);
        assertThat(primaryBean).isSameAs(dataSource);
    }
}
