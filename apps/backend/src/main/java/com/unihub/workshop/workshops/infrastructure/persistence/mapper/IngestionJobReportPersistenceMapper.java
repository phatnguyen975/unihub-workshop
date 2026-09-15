package com.unihub.workshop.workshops.infrastructure.persistence.mapper;

import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import com.unihub.workshop.workshops.infrastructure.persistence.IngestionJobReportJpaEntity;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface IngestionJobReportPersistenceMapper {
    IngestionJobReport toDomain(IngestionJobReportJpaEntity entity);

    List<IngestionJobReport> toDomainList(
        List<IngestionJobReportJpaEntity> entities
    );

    IngestionJobReportJpaEntity toEntity(IngestionJobReport domain);
}
