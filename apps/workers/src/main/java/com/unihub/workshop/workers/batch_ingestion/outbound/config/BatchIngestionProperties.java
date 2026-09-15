package com.unihub.workshop.workers.batch_ingestion.outbound.config;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.batch-ingestion")
@Getter
@Setter
public class BatchIngestionProperties {

    private Set<String> expectedFaculties = Set.of();
    private long maxFileSize = 104857600L; // default 100MB
}
