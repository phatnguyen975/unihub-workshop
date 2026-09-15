package com.unihub.workshop.workers.batch_ingestion.outbound.persistence.mapper;

import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory.OrganizationInfo;
import com.unihub.workshop.workers.batch_ingestion.outbound.persistence.OrganizationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrganizationPersistenceMapper {
    OrganizationInfo toDomain(OrganizationEntity entity);
}
