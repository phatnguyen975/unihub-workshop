package com.unihub.workshop.iam.presentation.controller;

import com.unihub.workshop.iam.application.dto.ChangePasswordRequest;
import com.unihub.workshop.iam.application.dto.ChangePasswordResponse;
import com.unihub.workshop.iam.application.dto.CreateOrganizationRequest;
import com.unihub.workshop.iam.application.dto.LogoutRequest;
import com.unihub.workshop.iam.application.dto.OrganizationResponse;
import com.unihub.workshop.iam.application.dto.OrganizationStaffPageResponse;
import com.unihub.workshop.iam.application.dto.ProvisionStaffRequest;
import com.unihub.workshop.iam.application.dto.ProvisionStaffResponse;
import com.unihub.workshop.iam.application.dto.UpdateOrganizationRequest;
import com.unihub.workshop.iam.application.dto.UpdateProfileRequest;
import com.unihub.workshop.iam.application.dto.UpdateStaffStatusRequest;
import com.unihub.workshop.iam.application.dto.UpdateStaffStatusResponse;
import com.unihub.workshop.iam.application.dto.UserProfileResponse;
import com.unihub.workshop.iam.application.usecases.AuthUseCase;
import com.unihub.workshop.iam.application.usecases.UseCaseResult;
import com.unihub.workshop.iam.application.usecases.UserUseCase;
import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.web.ResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private final AuthUseCase authenticationUseCase;
    private final UserUseCase userUseCase;
    private final ResponseFactory responseFactory;

    public UserController(
            AuthUseCase authenticationUseCase,
            UserUseCase userUseCase,
            ResponseFactory responseFactory) {
        this.authenticationUseCase = authenticationUseCase;
        this.userUseCase = userUseCase;
        this.responseFactory = responseFactory;
    }

    @PostMapping("/users/me/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request,
            HttpServletRequest httpRequest) {
        String userId = requireUserId();
        String accessToken = extractBearerToken(httpRequest.getHeader("Authorization"));
        UseCaseResult<Void> result = authenticationUseCase.logout(userId, request, accessToken);
        ApiResponse<Void> body = responseFactory.success(result.data(), result.message(), result.status());
        SecurityContextHolder.clearContext();
        return ResponseEntity.status(result.status()).body(body);
    }

    @PutMapping("/users/me/password")
    public ResponseEntity<ApiResponse<ChangePasswordResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        String userId = requireUserId();
        UseCaseResult<ChangePasswordResponse> result = authenticationUseCase.changePassword(userId, request);
        ApiResponse<ChangePasswordResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        String userId = requireUserId();
        UseCaseResult<UserProfileResponse> result = userUseCase.getProfile(userId);
        ApiResponse<UserProfileResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PatchMapping("/users/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        String userId = requireUserId();
        UseCaseResult<UserProfileResponse> result = userUseCase.updateProfile(userId, request);
        ApiResponse<UserProfileResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PreAuthorize("hasAuthority('ORGANIZER')")
    @PostMapping("/organizations")
    public ResponseEntity<ApiResponse<OrganizationResponse>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        String userId = requireUserId();
        UseCaseResult<OrganizationResponse> result = userUseCase.createOrganization(userId, request);
        ApiResponse<OrganizationResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PreAuthorize("hasAuthority('ORGANIZER')")
    @PatchMapping("/organizations/{id}")
    public ResponseEntity<ApiResponse<OrganizationResponse>> updateOrganization(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        String userId = requireUserId();
        UseCaseResult<OrganizationResponse> result = userUseCase.updateOrganization(userId, id, request);
        ApiResponse<OrganizationResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PreAuthorize("hasAuthority('ORGANIZER')")
    @PostMapping("/organizations/{organizationId}/staff")
    public ResponseEntity<ApiResponse<ProvisionStaffResponse>> provisionInternalAccount(
            @PathVariable("organizationId") String organizationId,
            @Valid @RequestBody ProvisionStaffRequest request) {
        String userId = requireUserId();
        UseCaseResult<ProvisionStaffResponse> result = userUseCase.provisionInternalAccount(userId, organizationId, request);
        ApiResponse<ProvisionStaffResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PreAuthorize("hasAuthority('ORGANIZER')")
    @GetMapping("/organizations/{organizationId}/staff")
    public ResponseEntity<ApiResponse<OrganizationStaffPageResponse>> listOrganizationStaff(
            @PathVariable("organizationId") String organizationId,
            @PageableDefault(size = 20) Pageable pageable) {
        String userId = requireUserId();
        UseCaseResult<OrganizationStaffPageResponse> result = userUseCase.listOrganizationStaff(userId, organizationId, pageable);
        ApiResponse<OrganizationStaffPageResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PreAuthorize("hasAuthority('ORGANIZER')")
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<ApiResponse<UpdateStaffStatusResponse>> manageStaffStatus(
            @PathVariable("userId") String targetUserId,
            @Valid @RequestBody UpdateStaffStatusRequest request) {
        String userId = requireUserId();
        UseCaseResult<UpdateStaffStatusResponse> result = userUseCase.manageStaffStatus(userId, targetUserId, request);
        ApiResponse<UpdateStaffStatusResponse> body = responseFactory.success(result.data(), result.message(), result.status());
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

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String trimmed = authorization.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = trimmed.substring(7).trim();
        return token.isBlank() ? null : token;
    }
}
