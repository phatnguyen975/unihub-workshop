package com.unihub.workshop.workers.batch_ingestion.outbound.config;

import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionJobReportWriter;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;
import com.unihub.workshop.workers.batch_ingestion.core.usecases.FileAdmissionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BatchIngestionConfig {

    @Bean
    public FileAdmissionService fileAdmissionService(
            IngestionObjectStore objectStore,
            OrganizationDirectory organizationDirectory,
            IngestionJobReportWriter reportWriter,
            IngestionEventPublisher eventPublisher,
            BatchIngestionProperties properties) {
        return new FileAdmissionService(
                objectStore,
                organizationDirectory,
                reportWriter,
                eventPublisher,
                properties.getExpectedFaculties());
    }
}
