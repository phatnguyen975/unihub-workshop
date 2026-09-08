package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.ChangeUserPasswordUseCase;
import com.unihub.workshop.iam.application.ports.in.GetUserProfileUseCase;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileCommand;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.presentation.dto.ChangePasswordRequest;
import com.unihub.workshop.iam.presentation.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.presentation.dto.UserProfileResponse;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;
    private final ChangeUserPasswordUseCase changeUserPasswordUseCase;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(Authentication authentication) {
        UUID userId = SecurityContextUtils.extractUserId(authentication);
        User user = getUserProfileUseCase.getProfile(userId);
        return ApiResponse.success(UserProfileResponse.from(user), "Profile retrieved successfully.");
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(
            Authentication authentication,
            @RequestBody(required = false) Map<String, Object> body) {
        UUID userId = SecurityContextUtils.extractUserId(authentication);
        if (body == null) {
            throw new ValidationException("Request body must not be null");
        }
        UpdateProfileRequest request = UpdateProfileRequest.from(body);
        UpdateUserProfileCommand command = request.toCommand();
        User updated = updateUserProfileUseCase.updateProfile(userId, command);
        return ApiResponse.success(UserProfileResponse.from(updated), "Profile updated successfully.");
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changeMyPassword(
            Authentication authentication,
            @Valid @RequestBody(required = false) ChangePasswordRequest request) {
        if (request == null) {
            throw new ValidationException("Request body must not be null");
        }
        UUID userId = SecurityContextUtils.extractUserId(authentication);
        UUID currentSessionId = SecurityContextUtils.extractRequiredSessionId(
                authentication, "Current session ID (sid) is required for password change");
        changeUserPasswordUseCase.changePassword(
                userId, currentSessionId, request.currentPassword(), request.newPassword());
        return ApiResponse.success(null, "Password changed successfully.");
    }
}
