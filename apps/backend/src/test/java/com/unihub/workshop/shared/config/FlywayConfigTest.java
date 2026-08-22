package com.unihub.workshop.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayConfigTest {

    @Test
    void flywayProperties_areCorrectlyConfigured() {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new ClassPathResource("application.yml"));
        Properties properties = yamlFactory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(properties.getProperty("spring.flyway.out-of-order")).isEqualTo("false");
        assertThat(properties.getProperty("spring.flyway.validate-on-migrate")).isEqualTo("true");
    }
}
