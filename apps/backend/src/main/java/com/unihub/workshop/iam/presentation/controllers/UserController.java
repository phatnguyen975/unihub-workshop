package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.GetUserProfileUseCase;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileCommand;
import com.unihub.workshop.iam.application.ports.in.UpdateUserProfileUseCase;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.presentation.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.presentation.dto.UserProfileResponse;
import com.unihub.workshop.shared.exception.UnauthorizedException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateUserProfileUseCase updateUserProfileUseCase;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        User user = getUserProfileUseCase.getProfile(userId);
        return ApiResponse.success(UserProfileResponse.from(user), "Profile retrieved successfully.");
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(
            Authentication authentication,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        UUID userId = extractUserId(authentication);
        if (body == null) {
            throw new ValidationException("Request body must not be null");
        }
        UpdateProfileRequest request = UpdateProfileRequest.from(body);
        UpdateUserProfileCommand command = request.toCommand();
        User updated = updateUserProfileUseCase.updateProfile(userId, command);
        return ApiResponse.success(UserProfileResponse.from(updated), "Profile updated successfully.");
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        if (authentication.getPrincipal() instanceof JwtAuthenticatedPrincipal jwtPrincipal) {
            return UUID.fromString(jwtPrincipal.sub());
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        } else {
            throw new UnauthorizedException("Unauthorized: invalid token principal");
        }
    }
}
