package com.unihub.workshop.iam.infrastructure.persistence.mapper;

import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.infrastructure.persistence.UserJpaEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserPersistenceMapper {
    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "isTemporaryPassword", source = "temporaryPassword")
    User toDomain(UserJpaEntity entity);

    List<User> toDomainList(List<UserJpaEntity> entities);

    UserJpaEntity toEntity(User domain);
}
