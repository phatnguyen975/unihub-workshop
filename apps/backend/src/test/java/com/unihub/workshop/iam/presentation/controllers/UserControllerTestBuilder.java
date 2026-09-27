package com.unihub.workshop.iam.presentation.controllers;

import static org.mockito.Mockito.mock;

import com.unihub.workshop.iam.application.ports.in.ChangeUserPasswordUseCase;
import com.unihub.workshop.iam.application.ports.in.GetUserProfileUseCase;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileUseCase;
import com.unihub.workshop.iam.application.ports.in.UploadUserAvatarUseCase;
import com.unihub.workshop.iam.presentation.mapper.UserPresentationMapper;
import com.unihub.workshop.iam.presentation.mapper.UserPresentationMapperImpl;

public class UserControllerTestBuilder {

    private GetUserProfileUseCase getUserProfileUseCase = mock(
        GetUserProfileUseCase.class
    );
    private UpdateUserProfileUseCase updateUserProfileUseCase = mock(
        UpdateUserProfileUseCase.class
    );
    private UploadUserAvatarUseCase uploadUserAvatarUseCase = mock(
        UploadUserAvatarUseCase.class
    );
    private ChangeUserPasswordUseCase changeUserPasswordUseCase = mock(
        ChangeUserPasswordUseCase.class
    );
    private UserPresentationMapper userMapper =
        new UserPresentationMapperImpl();

    public static UserControllerTestBuilder builder() {
        return new UserControllerTestBuilder();
    }

    public UserControllerTestBuilder withGetUserProfileUseCase(
        GetUserProfileUseCase getUserProfileUseCase
    ) {
        this.getUserProfileUseCase = getUserProfileUseCase;
        return this;
    }

    public UserControllerTestBuilder withUpdateUserProfileUseCase(
        UpdateUserProfileUseCase updateUserProfileUseCase
    ) {
        this.updateUserProfileUseCase = updateUserProfileUseCase;
        return this;
    }

    public UserControllerTestBuilder withUploadUserAvatarUseCase(
        UploadUserAvatarUseCase uploadUserAvatarUseCase
    ) {
        this.uploadUserAvatarUseCase = uploadUserAvatarUseCase;
        return this;
    }

    public UserControllerTestBuilder withChangeUserPasswordUseCase(
        ChangeUserPasswordUseCase changeUserPasswordUseCase
    ) {
        this.changeUserPasswordUseCase = changeUserPasswordUseCase;
        return this;
    }

    public UserControllerTestBuilder withUserMapper(
        UserPresentationMapper userMapper
    ) {
        this.userMapper = userMapper;
        return this;
    }

    public UserController build() {
        return new UserController(
            getUserProfileUseCase,
            updateUserProfileUseCase,
            uploadUserAvatarUseCase,
            changeUserPasswordUseCase,
            userMapper
        );
    }
}
