package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.Mockito.mock;

import com.unihub.workshop.iam.application.ports.in.GetOrganizationMemberUseCase;
import com.unihub.workshop.iam.application.ports.in.ListOrganizationMembersUseCase;
import com.unihub.workshop.iam.application.ports.in.ProvisionUserUseCase;
import com.unihub.workshop.iam.application.ports.in.UpdateMemberStatusUseCase;
import com.unihub.workshop.iam.presentation.mapper.OrganizationPresentationMapper;
import com.unihub.workshop.iam.presentation.mapper.OrganizationPresentationMapperImpl;

public class OrganizationControllerTestBuilder {

    private ListOrganizationMembersUseCase listOrganizationMembersUseCase =
        mock(ListOrganizationMembersUseCase.class);
    private GetOrganizationMemberUseCase getOrganizationMemberUseCase = mock(
        GetOrganizationMemberUseCase.class
    );
    private ProvisionUserUseCase provisionUserUseCase = mock(
        ProvisionUserUseCase.class
    );
    private UpdateMemberStatusUseCase updateMemberStatusUseCase = mock(
        UpdateMemberStatusUseCase.class
    );
    private OrganizationPresentationMapper organizationMapper =
        new OrganizationPresentationMapperImpl();

    public static OrganizationControllerTestBuilder builder() {
        return new OrganizationControllerTestBuilder();
    }

    public OrganizationControllerTestBuilder withListOrganizationMembersUseCase(
        ListOrganizationMembersUseCase listOrganizationMembersUseCase
    ) {
        this.listOrganizationMembersUseCase = listOrganizationMembersUseCase;
        return this;
    }

    public OrganizationControllerTestBuilder withGetOrganizationMemberUseCase(
        GetOrganizationMemberUseCase getOrganizationMemberUseCase
    ) {
        this.getOrganizationMemberUseCase = getOrganizationMemberUseCase;
        return this;
    }

    public OrganizationControllerTestBuilder withProvisionUserUseCase(
        ProvisionUserUseCase provisionUserUseCase
    ) {
        this.provisionUserUseCase = provisionUserUseCase;
        return this;
    }

    public OrganizationControllerTestBuilder withUpdateMemberStatusUseCase(
        UpdateMemberStatusUseCase updateMemberStatusUseCase
    ) {
        this.updateMemberStatusUseCase = updateMemberStatusUseCase;
        return this;
    }

    public OrganizationControllerTestBuilder withOrganizationMapper(
        OrganizationPresentationMapper organizationMapper
    ) {
        this.organizationMapper = organizationMapper;
        return this;
    }

    public OrganizationController build() {
        return new OrganizationController(
            listOrganizationMembersUseCase,
            getOrganizationMemberUseCase,
            provisionUserUseCase,
            updateMemberStatusUseCase,
            organizationMapper
        );
    }
}
