package com.unihub.workshop.workers.batch_ingestion.outbound.persistence.mapper;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import com.unihub.workshop.workers.batch_ingestion.outbound.persistence.IngestionJobReportEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface IngestionJobReportPersistenceMapper {
    IngestionJobReportEntity toEntity(IngestionJobReport domain);

    IngestionJobReport toDomain(IngestionJobReportEntity entity);
}
