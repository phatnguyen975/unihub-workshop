package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.GetOrganizationMemberUseCase;
import com.unihub.workshop.iam.application.ports.in.ListOrganizationMembersUseCase;
import com.unihub.workshop.iam.application.ports.in.ProvisionUserUseCase;

import static org.mockito.Mockito.mock;

public class OrganizationUserControllerTestBuilder {

    private ListOrganizationMembersUseCase listOrganizationMembersUseCase = mock(ListOrganizationMembersUseCase.class);
    private GetOrganizationMemberUseCase getOrganizationMemberUseCase = mock(GetOrganizationMemberUseCase.class);
    private ProvisionUserUseCase provisionUserUseCase = mock(ProvisionUserUseCase.class);

    public static OrganizationUserControllerTestBuilder builder() {
        return new OrganizationUserControllerTestBuilder();
    }

    public OrganizationUserControllerTestBuilder withListOrganizationMembersUseCase(
            ListOrganizationMembersUseCase listOrganizationMembersUseCase) {
        this.listOrganizationMembersUseCase = listOrganizationMembersUseCase;
        return this;
    }

    public OrganizationUserControllerTestBuilder withGetOrganizationMemberUseCase(
            GetOrganizationMemberUseCase getOrganizationMemberUseCase) {
        this.getOrganizationMemberUseCase = getOrganizationMemberUseCase;
        return this;
    }

    public OrganizationUserControllerTestBuilder withProvisionUserUseCase(
            ProvisionUserUseCase provisionUserUseCase) {
        this.provisionUserUseCase = provisionUserUseCase;
        return this;
    }

    public OrganizationUserController build() {
        return new OrganizationUserController(
                listOrganizationMembersUseCase,
                getOrganizationMemberUseCase,
                provisionUserUseCase);
    }
}
