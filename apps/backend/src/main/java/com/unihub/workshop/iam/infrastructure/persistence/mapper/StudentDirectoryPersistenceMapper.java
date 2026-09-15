package com.unihub.workshop.iam.infrastructure.persistence.mapper;

import com.unihub.workshop.iam.domain.model.StudentDirectory;
import com.unihub.workshop.iam.infrastructure.persistence.StudentDirectoryJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StudentDirectoryPersistenceMapper {
    StudentDirectory toDomain(StudentDirectoryJpaEntity entity);

    StudentDirectoryJpaEntity toEntity(StudentDirectory domain);
}
