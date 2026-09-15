package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionJobReportWriter;
import com.unihub.workshop.workers.batch_ingestion.outbound.persistence.mapper.IngestionJobReportPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostgresIngestionJobReportWriter
    implements IngestionJobReportWriter
{

    private final IngestionJobReportRepository repository;
    private final IngestionJobReportPersistenceMapper mapper;

    @Override
    public void save(IngestionJobReport report) {
        repository.save(mapper.toEntity(report));
    }
}
