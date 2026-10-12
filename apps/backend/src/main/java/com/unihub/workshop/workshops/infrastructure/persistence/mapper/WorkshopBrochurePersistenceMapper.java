package com.unihub.workshop.workshops.infrastructure.persistence.mapper;

import com.unihub.workshop.workshops.domain.model.WorkshopBrochure;
import com.unihub.workshop.workshops.infrastructure.persistence.WorkshopBrochureJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkshopBrochurePersistenceMapper {
    WorkshopBrochure toDomain(WorkshopBrochureJpaEntity entity);

    WorkshopBrochureJpaEntity toEntity(WorkshopBrochure domain);
}
