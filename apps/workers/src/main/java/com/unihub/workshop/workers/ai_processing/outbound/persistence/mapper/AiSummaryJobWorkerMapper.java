package com.unihub.workshop.workers.ai_processing.outbound.persistence.mapper;

import com.unihub.workshop.workers.ai_processing.core.models.AiSummaryJobModel;
import com.unihub.workshop.workers.ai_processing.outbound.persistence.AiSummaryJobJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AiSummaryJobWorkerMapper {
    @Mapping(
        target = "status",
        expression = "java(entity.getStatus() != null ? AiSummaryJobStatusModel.valueOf(entity.getStatus()) : null)"
    )
    AiSummaryJobModel toModel(AiSummaryJobJpaEntity entity);

    @Mapping(
        target = "status",
        expression = "java(model.status() != null ? model.status().name() : null)"
    )
    AiSummaryJobJpaEntity toEntity(AiSummaryJobModel model);
}
