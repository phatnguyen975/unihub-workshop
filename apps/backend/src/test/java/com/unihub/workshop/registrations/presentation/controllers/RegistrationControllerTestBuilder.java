package com.unihub.workshop.registrations.presentation.controllers;

import static org.mockito.Mockito.mock;

import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsUseCase;
import com.unihub.workshop.registrations.application.ports.in.GetRegistrationDetailUseCase;
import com.unihub.workshop.registrations.application.ports.in.RegisterWorkshopUseCase;
import com.unihub.workshop.registrations.presentation.mapper.RegistrationPresentationMapper;
import org.mapstruct.factory.Mappers;

public class RegistrationControllerTestBuilder {

    private RegisterWorkshopUseCase registerWorkshopUseCase = mock(
        RegisterWorkshopUseCase.class
    );
    private GetMyRegistrationsUseCase getMyRegistrationsUseCase = mock(
        GetMyRegistrationsUseCase.class
    );
    private GetRegistrationDetailUseCase getRegistrationDetailUseCase = mock(
        GetRegistrationDetailUseCase.class
    );
    private RegistrationPresentationMapper registrationMapper =
        Mappers.getMapper(RegistrationPresentationMapper.class);

    public static RegistrationControllerTestBuilder builder() {
        return new RegistrationControllerTestBuilder();
    }

    public RegistrationControllerTestBuilder withRegisterWorkshopUseCase(
        RegisterWorkshopUseCase registerWorkshopUseCase
    ) {
        this.registerWorkshopUseCase = registerWorkshopUseCase;
        return this;
    }

    public RegistrationControllerTestBuilder withGetMyRegistrationsUseCase(
        GetMyRegistrationsUseCase getMyRegistrationsUseCase
    ) {
        this.getMyRegistrationsUseCase = getMyRegistrationsUseCase;
        return this;
    }

    public RegistrationControllerTestBuilder withGetRegistrationDetailUseCase(
        GetRegistrationDetailUseCase getRegistrationDetailUseCase
    ) {
        this.getRegistrationDetailUseCase = getRegistrationDetailUseCase;
        return this;
    }

    public RegistrationControllerTestBuilder withRegistrationMapper(
        RegistrationPresentationMapper registrationMapper
    ) {
        this.registrationMapper = registrationMapper;
        return this;
    }

    public RegistrationController build() {
        return new RegistrationController(
            registerWorkshopUseCase,
            getMyRegistrationsUseCase,
            getRegistrationDetailUseCase,
            registrationMapper
        );
    }
}
