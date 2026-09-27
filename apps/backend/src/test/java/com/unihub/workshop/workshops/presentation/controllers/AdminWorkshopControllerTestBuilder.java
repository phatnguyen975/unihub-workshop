package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.Mockito.mock;

import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.workshops.application.ports.in.DeleteDraftUseCase;
import com.unihub.workshop.workshops.application.ports.in.GetWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftUseCase;
import com.unihub.workshop.workshops.presentation.mapper.WorkshopPresentationMapper;
import org.mapstruct.factory.Mappers;

public class AdminWorkshopControllerTestBuilder {

    private SaveDraftUseCase saveDraftUseCase = mock(SaveDraftUseCase.class);
    private GetWorkshopUseCase getWorkshopUseCase = mock(
        GetWorkshopUseCase.class
    );
    private DeleteDraftUseCase deleteDraftUseCase = mock(
        DeleteDraftUseCase.class
    );
    private WorkshopPresentationMapper workshopMapper = Mappers.getMapper(
        WorkshopPresentationMapper.class
    );
    private MediaStoragePort mediaStoragePort = mock(MediaStoragePort.class);

    public static AdminWorkshopControllerTestBuilder builder() {
        return new AdminWorkshopControllerTestBuilder();
    }

    public AdminWorkshopControllerTestBuilder withSaveDraftUseCase(
        SaveDraftUseCase saveDraftUseCase
    ) {
        this.saveDraftUseCase = saveDraftUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withGetWorkshopUseCase(
        GetWorkshopUseCase getWorkshopUseCase
    ) {
        this.getWorkshopUseCase = getWorkshopUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withDeleteDraftUseCase(
        DeleteDraftUseCase deleteDraftUseCase
    ) {
        this.deleteDraftUseCase = deleteDraftUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withWorkshopMapper(
        WorkshopPresentationMapper workshopMapper
    ) {
        this.workshopMapper = workshopMapper;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withMediaStoragePort(
        MediaStoragePort mediaStoragePort
    ) {
        this.mediaStoragePort = mediaStoragePort;
        return this;
    }

    public AdminWorkshopController build() {
        return new AdminWorkshopController(
            saveDraftUseCase,
            getWorkshopUseCase,
            deleteDraftUseCase,
            workshopMapper,
            mediaStoragePort
        );
    }
}
