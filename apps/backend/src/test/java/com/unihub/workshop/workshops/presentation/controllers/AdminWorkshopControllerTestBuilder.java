package com.unihub.workshop.workshops.presentation.controllers;

import static org.mockito.Mockito.mock;

import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.workshops.application.ports.in.AssignStaffUseCase;
import com.unihub.workshop.workshops.application.ports.in.DeleteDraftUseCase;
import com.unihub.workshop.workshops.application.ports.in.GetWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsUseCase;
import com.unihub.workshop.workshops.application.ports.in.ListAssignedStaffUseCase;
import com.unihub.workshop.workshops.application.ports.in.ListAvailableStaffUseCase;
import com.unihub.workshop.workshops.application.ports.in.PublishWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.RescheduleWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.in.RevokeStaffUseCase;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftUseCase;
import com.unihub.workshop.workshops.application.ports.in.UpdateWorkshopUseCase;
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
    private ListAdminWorkshopsUseCase listAdminWorkshopsUseCase = mock(
        ListAdminWorkshopsUseCase.class
    );
    private ListAssignedStaffUseCase listAssignedStaffUseCase = mock(
        ListAssignedStaffUseCase.class
    );
    private ListAvailableStaffUseCase listAvailableStaffUseCase = mock(
        ListAvailableStaffUseCase.class
    );
    private AssignStaffUseCase assignStaffUseCase = mock(
        AssignStaffUseCase.class
    );
    private RevokeStaffUseCase revokeStaffUseCase = mock(
        RevokeStaffUseCase.class
    );
    private PublishWorkshopUseCase publishWorkshopUseCase = mock(
        PublishWorkshopUseCase.class
    );
    private UpdateWorkshopUseCase updateWorkshopUseCase = mock(
        UpdateWorkshopUseCase.class
    );
    private RescheduleWorkshopUseCase rescheduleWorkshopUseCase = mock(
        RescheduleWorkshopUseCase.class
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

    public AdminWorkshopControllerTestBuilder withListAdminWorkshopsUseCase(
        ListAdminWorkshopsUseCase listAdminWorkshopsUseCase
    ) {
        this.listAdminWorkshopsUseCase = listAdminWorkshopsUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withListAssignedStaffUseCase(
        ListAssignedStaffUseCase listAssignedStaffUseCase
    ) {
        this.listAssignedStaffUseCase = listAssignedStaffUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withListAvailableStaffUseCase(
        ListAvailableStaffUseCase listAvailableStaffUseCase
    ) {
        this.listAvailableStaffUseCase = listAvailableStaffUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withAssignStaffUseCase(
        AssignStaffUseCase assignStaffUseCase
    ) {
        this.assignStaffUseCase = assignStaffUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withRevokeStaffUseCase(
        RevokeStaffUseCase revokeStaffUseCase
    ) {
        this.revokeStaffUseCase = revokeStaffUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withPublishWorkshopUseCase(
        PublishWorkshopUseCase publishWorkshopUseCase
    ) {
        this.publishWorkshopUseCase = publishWorkshopUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withUpdateWorkshopUseCase(
        UpdateWorkshopUseCase updateWorkshopUseCase
    ) {
        this.updateWorkshopUseCase = updateWorkshopUseCase;
        return this;
    }

    public AdminWorkshopControllerTestBuilder withRescheduleWorkshopUseCase(
        RescheduleWorkshopUseCase rescheduleWorkshopUseCase
    ) {
        this.rescheduleWorkshopUseCase = rescheduleWorkshopUseCase;
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
            listAdminWorkshopsUseCase,
            listAssignedStaffUseCase,
            listAvailableStaffUseCase,
            assignStaffUseCase,
            revokeStaffUseCase,
            publishWorkshopUseCase,
            updateWorkshopUseCase,
            rescheduleWorkshopUseCase,
            workshopMapper,
            mediaStoragePort
        );
    }
}
