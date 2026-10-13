package com.unihub.workshop.registrations.presentation.mapper;

import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.presentation.dto.RegistrationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RegistrationPresentationMapper {
    @Mapping(target = "studentId", source = "studentUserId")
    @Mapping(target = "qrPayload", source = "qrTicket.qrPayload")
    @Mapping(target = "qrSignature", source = "qrTicket.qrSignature")
    @Mapping(target = "qrIssuedAt", source = "qrTicket.qrIssuedAt")
    RegistrationResponse toResponse(Registration registration);
}
