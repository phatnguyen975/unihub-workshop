package com.unihub.workshop.workshops.infrastructure.persistence.mapper;

import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.infrastructure.persistence.WorkshopJpaEntity;
import com.unihub.workshop.workshops.infrastructure.persistence.WorkshopSpeakerJpaEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkshopPersistenceMapper {
    @Mapping(target = "speakers", expression = "java(java.util.List.of())")
    Workshop toDomain(WorkshopJpaEntity entity);

    WorkshopJpaEntity toEntity(Workshop domain);

    List<Workshop> toDomainList(List<WorkshopJpaEntity> entities);

    Speaker toSpeakerDomain(WorkshopSpeakerJpaEntity entity);

    List<Speaker> toSpeakerDomainList(List<WorkshopSpeakerJpaEntity> entities);

    WorkshopSpeakerJpaEntity toSpeakerEntity(Speaker domain);

    List<WorkshopSpeakerJpaEntity> toSpeakerEntityList(List<Speaker> domains);
}
