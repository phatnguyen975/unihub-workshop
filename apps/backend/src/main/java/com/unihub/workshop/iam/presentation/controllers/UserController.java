package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.ChangeUserPasswordUseCase;
import com.unihub.workshop.iam.application.ports.in.GetUserProfileUseCase;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileCommand;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.presentation.dto.ChangePasswordRequest;
import com.unihub.workshop.iam.presentation.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.presentation.dto.UserProfileResponse;
import com.unihub.workshop.iam.presentation.mapper.UserPresentationMapper;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;
    private final ChangeUserPasswordUseCase changeUserPasswordUseCase;
    private final UserPresentationMapper userMapper;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(
        Authentication authentication
    ) {
        UUID userId = SecurityContextUtils.extractUserId(authentication);
        User user = getUserProfileUseCase.getProfile(userId);
        return ApiResponse.success(
            userMapper.toProfileResponse(user),
            "Profile retrieved successfully."
        );
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(
        Authentication authentication,
        @RequestBody Map<String, Object> body
    ) {
        UUID userId = SecurityContextUtils.extractUserId(authentication);
        UpdateProfileRequest request = UpdateProfileRequest.from(body);
        UpdateUserProfileCommand command = request.toCommand();
        User updated = updateUserProfileUseCase.updateProfile(userId, command);
        return ApiResponse.success(
            userMapper.toProfileResponse(updated),
            "Profile updated successfully."
        );
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changeMyPassword(
        Authentication authentication,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID userId = SecurityContextUtils.extractUserId(authentication);
        UUID currentSessionId = SecurityContextUtils.extractSessionId(
            authentication
        );
        changeUserPasswordUseCase.changePassword(
            userId,
            currentSessionId,
            request.currentPassword(),
            request.newPassword()
        );
        return ApiResponse.success(null, "Password changed successfully.");
    }
}
