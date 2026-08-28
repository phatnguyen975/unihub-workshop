package com.unihub.workshop.shared.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class DevChaosMockAdapterTest {

    @Nested
    @SpringBootTest(properties = {
            "spring.flyway.enabled=false",
            "spring.data.redis.repositories.enabled=false",
            "datasource.primary.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
            "datasource.replica.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
            "datasource.primary.driver-class-name=org.h2.Driver",
            "datasource.replica.driver-class-name=org.h2.Driver",
            "chaos.latency-ms=200",
            "chaos.error-rate=1.0" // 100% error rate for predictable testing
    })
    @ActiveProfiles({ "dev", "test" })
    class DevProfileTest {

        @Autowired
        private ChaosMockAdapter chaosMockAdapter;

        @Test
        void shouldBeAvailableInDevProfile() {
            assertThat(chaosMockAdapter).isNotNull();
            assertThat(chaosMockAdapter).isInstanceOf(DevChaosMockAdapter.class);
        }

        @Test
        void shouldInjectLatencyAndError() {
            long start = System.currentTimeMillis();

            // Should throw exception because error-rate is 1.0
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> chaosMockAdapter.applyChaos())
                    .withMessageContaining("Simulated chaos error");

            long end = System.currentTimeMillis();

            // Should have waited at least 200ms
            assertThat(end - start).isGreaterThanOrEqualTo(150); // slight buffer for timing variance
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.flyway.enabled=false",
            "spring.data.redis.repositories.enabled=false",
            "datasource.primary.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
            "datasource.replica.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
            "datasource.primary.driver-class-name=org.h2.Driver",
            "datasource.replica.driver-class-name=org.h2.Driver"
    })
    @ActiveProfiles({ "prod", "test" })
    class ProdProfileTest {
        @Autowired
        private ApplicationContext context;

        @Test
        void shouldNotBeAvailableInProdProfile() {
            assertThatExceptionOfType(NoSuchBeanDefinitionException.class)
                    .isThrownBy(() -> context.getBean(ChaosMockAdapter.class));
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.flyway.enabled=false",
            "spring.data.redis.repositories.enabled=false",
            "datasource.primary.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
            "datasource.replica.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
            "datasource.primary.driver-class-name=org.h2.Driver",
            "datasource.replica.driver-class-name=org.h2.Driver",
            "chaos.latency-ms=0",
            "chaos.error-rate=0.0"
    })
    @ActiveProfiles({ "dev", "test" })
    class DevProfileNoChaosTest {
        @Autowired
        private ChaosMockAdapter chaosMockAdapter;

        @Test
        void shouldNotThrowErrorWhenRateIsZero() {
            assertThatNoException().isThrownBy(() -> chaosMockAdapter.applyChaos());
        }
    }
}
