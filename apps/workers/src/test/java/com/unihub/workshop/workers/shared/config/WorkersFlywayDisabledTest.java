package com.unihub.workshop.workers.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class WorkersFlywayDisabledTest {

    @Test
    void workersFlyway_isDisabledInProperties() {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new ClassPathResource("application.yml"));
        Properties properties = yamlFactory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo(
            "false"
        );
    }
}
