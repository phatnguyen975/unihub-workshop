package com.unihub.workshop.workshops.infrastructure.persistence.mapper;

import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.infrastructure.persistence.WorkshopJpaEntity;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkshopPersistenceMapper {
    Workshop toDomain(WorkshopJpaEntity entity);

    WorkshopJpaEntity toEntity(Workshop domain);

    List<Workshop> toDomainList(List<WorkshopJpaEntity> entities);
}
