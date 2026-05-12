package com.unihub.workshop.iam.application.usecases;

import com.unihub.workshop.iam.application.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.application.dto.UserProfileResponse;

public interface UserUseCase {

    UseCaseResult<UserProfileResponse> getProfile(String userId);

    UseCaseResult<UserProfileResponse> updateProfile(String userId, UpdateProfileRequest request);
}
