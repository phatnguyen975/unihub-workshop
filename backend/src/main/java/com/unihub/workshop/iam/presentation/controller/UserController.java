package com.unihub.workshop.iam.presentation.controller;

import com.unihub.workshop.iam.application.dto.ChangePasswordRequest;
import com.unihub.workshop.iam.application.dto.LogoutRequest;
import com.unihub.workshop.iam.application.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.application.dto.UserProfileResponse;
import com.unihub.workshop.iam.application.usecases.AuthenticationUseCase;
import com.unihub.workshop.iam.application.usecases.UseCaseResult;
import com.unihub.workshop.iam.application.usecases.UserUseCase;
import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.web.ResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private final AuthenticationUseCase authenticationUseCase;
    private final UserUseCase userUseCase;
    private final ResponseFactory responseFactory;

    public UserController(
            AuthenticationUseCase authenticationUseCase,
            UserUseCase userUseCase,
            ResponseFactory responseFactory) {
        this.authenticationUseCase = authenticationUseCase;
        this.userUseCase = userUseCase;
        this.responseFactory = responseFactory;
    }

    @PostMapping("/me/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request) {
        String userId = requireUserId();
        UseCaseResult<Void> result = authenticationUseCase.logout(userId, request);
        ApiResponse<Void> body = responseFactory.success(result.data(), result.message(), result.status());
        SecurityContextHolder.clearContext();
        return ResponseEntity.status(result.status()).body(body);
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        String userId = requireUserId();
        UseCaseResult<Void> result = authenticationUseCase.changePassword(userId, request);
        ApiResponse<Void> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        String userId = requireUserId();
        UseCaseResult<UserProfileResponse> result = userUseCase.getProfile(userId);
        ApiResponse<UserProfileResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        String userId = requireUserId();
        UseCaseResult<UserProfileResponse> result = userUseCase.updateProfile(userId, request);
        ApiResponse<UserProfileResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    private String requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Missing authentication", "unauthorized");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String subject && !subject.isBlank() && !"anonymousUser".equals(subject)) {
            return subject;
        }
        throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid authentication", "unauthorized");
    }
}
