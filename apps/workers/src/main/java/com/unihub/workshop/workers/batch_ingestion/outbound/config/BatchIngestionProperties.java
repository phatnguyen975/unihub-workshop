package com.unihub.workshop.workers.batch_ingestion.outbound.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@ConfigurationProperties(prefix = "app.batch-ingestion")
public class BatchIngestionProperties {

    private Set<String> expectedFaculties = Set.of();

    public Set<String> getExpectedFaculties() {
        return expectedFaculties;
    }

    public void setExpectedFaculties(Set<String> expectedFaculties) {
        this.expectedFaculties = expectedFaculties;
    }
}
