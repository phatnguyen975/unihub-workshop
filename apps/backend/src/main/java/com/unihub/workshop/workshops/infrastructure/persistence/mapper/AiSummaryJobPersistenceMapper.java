package com.unihub.workshop.workshops.infrastructure.persistence.mapper;

import com.unihub.workshop.workshops.domain.model.AiSummaryJob;
import com.unihub.workshop.workshops.domain.model.AiSummaryJobStatus;
import com.unihub.workshop.workshops.infrastructure.persistence.AiSummaryJobJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface AiSummaryJobPersistenceMapper {
    @Mapping(
        target = "status",
        source = "status",
        qualifiedByName = "stringToStatus"
    )
    AiSummaryJob toDomain(AiSummaryJobJpaEntity entity);

    @Mapping(
        target = "status",
        source = "status",
        qualifiedByName = "statusToString"
    )
    AiSummaryJobJpaEntity toEntity(AiSummaryJob domain);

    @Named("stringToStatus")
    default AiSummaryJobStatus stringToStatus(String status) {
        if (status == null) {
            return null;
        }
        return AiSummaryJobStatus.valueOf(status);
    }

    @Named("statusToString")
    default String statusToString(AiSummaryJobStatus status) {
        if (status == null) {
            return null;
        }
        return status.name();
    }
}
