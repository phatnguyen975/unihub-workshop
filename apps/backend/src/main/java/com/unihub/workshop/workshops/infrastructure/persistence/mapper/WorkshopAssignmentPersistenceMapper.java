package com.unihub.workshop.workshops.infrastructure.persistence.mapper;

import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;
import com.unihub.workshop.workshops.infrastructure.persistence.WorkshopAssignmentJpaEntity;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkshopAssignmentPersistenceMapper {
    WorkshopAssignment toDomain(WorkshopAssignmentJpaEntity entity);

    WorkshopAssignmentJpaEntity toEntity(WorkshopAssignment domain);

    List<WorkshopAssignment> toDomainList(
        List<WorkshopAssignmentJpaEntity> entities
    );
}
