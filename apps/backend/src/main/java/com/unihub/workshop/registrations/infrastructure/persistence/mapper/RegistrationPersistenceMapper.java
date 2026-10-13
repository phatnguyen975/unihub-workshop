package com.unihub.workshop.registrations.infrastructure.persistence.mapper;

import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.infrastructure.persistence.RegistrationJpaEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RegistrationPersistenceMapper {
    @Mapping(target = "qrTicket", expression = "java(toQrTicket(entity))")
    Registration toDomain(RegistrationJpaEntity entity);

    List<Registration> toDomainList(List<RegistrationJpaEntity> entities);

    @Mapping(target = "qrPayload", source = "qrTicket.qrPayload")
    @Mapping(target = "qrSignature", source = "qrTicket.qrSignature")
    @Mapping(target = "qrIssuedAt", source = "qrTicket.qrIssuedAt")
    RegistrationJpaEntity toEntity(Registration domain);

    default QrTicket toQrTicket(RegistrationJpaEntity entity) {
        if (
            entity == null ||
            (entity.getQrPayload() == null &&
                entity.getQrSignature() == null &&
                entity.getQrIssuedAt() == null)
        ) {
            return null;
        }
        return new QrTicket(
            entity.getQrPayload(),
            entity.getQrSignature(),
            entity.getQrIssuedAt()
        );
    }
}
