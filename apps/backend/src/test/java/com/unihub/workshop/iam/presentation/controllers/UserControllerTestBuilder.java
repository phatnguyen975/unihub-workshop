package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.GetUserProfileUseCase;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileUseCase;

import static org.mockito.Mockito.mock;

public class UserControllerTestBuilder {

    private GetUserProfileUseCase getUserProfileUseCase = mock(GetUserProfileUseCase.class);
    private UpdateUserProfileUseCase updateUserProfileUseCase = mock(UpdateUserProfileUseCase.class);

    public static UserControllerTestBuilder builder() {
        return new UserControllerTestBuilder();
    }

    public UserControllerTestBuilder withGetUserProfileUseCase(GetUserProfileUseCase getUserProfileUseCase) {
        this.getUserProfileUseCase = getUserProfileUseCase;
        return this;
    }

    public UserControllerTestBuilder withUpdateUserProfileUseCase(UpdateUserProfileUseCase updateUserProfileUseCase) {
        this.updateUserProfileUseCase = updateUserProfileUseCase;
        return this;
    }

    public UserController build() {
        return new UserController(getUserProfileUseCase, updateUserProfileUseCase);
    }
}
