package com.unihub.workshop.iam.infrastructure.persistence.mapper;

import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import com.unihub.workshop.iam.infrastructure.persistence.RefreshTokenJpaEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RefreshTokenPersistenceMapper {
    RefreshToken toDomain(RefreshTokenJpaEntity entity);

    RefreshTokenJpaEntity toEntity(RefreshToken domain);

    @Mapping(target = "isCurrentSession", constant = "false")
    ActiveSession toActiveSession(RefreshTokenJpaEntity entity);

    List<ActiveSession> toActiveSessionList(
        List<RefreshTokenJpaEntity> entities
    );
}
