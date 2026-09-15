package com.unihub.workshop.iam.infrastructure.persistence.mapper;

import com.unihub.workshop.iam.domain.model.UserVerificationToken;
import com.unihub.workshop.iam.infrastructure.persistence.UserVerificationTokenJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserVerificationTokenPersistenceMapper {
    UserVerificationToken toDomain(UserVerificationTokenJpaEntity entity);

    UserVerificationTokenJpaEntity toEntity(UserVerificationToken domain);
}
