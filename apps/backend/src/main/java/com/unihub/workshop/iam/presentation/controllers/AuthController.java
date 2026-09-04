package com.unihub.workshop.iam.presentation.controllers;

import com.unihub.workshop.iam.application.ports.in.LoginUseCase;
import com.unihub.workshop.iam.application.ports.in.LogoutUseCase;
import com.unihub.workshop.iam.application.ports.in.RefreshTokenUseCase;
import com.unihub.workshop.iam.presentation.dto.LoginRequest;
import com.unihub.workshop.iam.presentation.dto.LoginResponse;
import com.unihub.workshop.iam.presentation.dto.LogoutRequest;
import com.unihub.workshop.iam.presentation.dto.RefreshTokenRequest;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.security.PublicKeyService;
import com.unihub.workshop.iam.presentation.dto.PublicKeyResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.unihub.workshop.iam.application.ports.in.RegisterUserUseCase;
import com.unihub.workshop.iam.presentation.dto.RegisterRequest;
import com.unihub.workshop.iam.application.ports.in.ActivateAccountUseCase;
import com.unihub.workshop.iam.application.ports.in.ResendOtpUseCase;
import com.unihub.workshop.iam.presentation.dto.ActivateAccountRequest;
import com.unihub.workshop.iam.presentation.dto.RegisterResponse;
import com.unihub.workshop.iam.presentation.dto.ResendOtpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final PublicKeyService publicKeyService;
    private final RegisterUserUseCase registerUserUseCase;
    private final ActivateAccountUseCase activateAccountUseCase;
    private final ResendOtpUseCase resendOtpUseCase;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = registerUserUseCase.register(request.email(), request.password(), request.fullName());
        return ApiResponse.success(new RegisterResponse(userId),
                "Registration successful. Please check your email for the activation code.");
    }

    @PostMapping("/activate")
    public ApiResponse<Void> activate(@Valid @RequestBody ActivateAccountRequest request) {
        activateAccountUseCase.activate(request.email(), request.otp());
        return ApiResponse.success(null, "Account activated successfully. You may now log in.");
    }

    @PostMapping("/otp/resend")
    public ApiResponse<Void> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        resendOtpUseCase.resendOtp(request.email());
        return ApiResponse.success(null, "A new activation code has been sent to your email.");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = httpRequest.getRemoteAddr();

        LoginUseCase.LoginResult result = loginUseCase.login(
                request.email(),
                request.password(),
                userAgent,
                ipAddress);

        LoginResponse response = new LoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn());

        return ApiResponse.success(response, "Login successful");
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = httpRequest.getRemoteAddr();

        LoginUseCase.LoginResult result = refreshTokenUseCase.refresh(
                request.refreshToken(),
                userAgent,
                ipAddress);

        LoginResponse response = new LoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.expiresIn());

        return ApiResponse.success(response, "Token refreshed");
    }

    @GetMapping("/public-key")
    @PreAuthorize("hasRole('STAFF')")
    public ApiResponse<PublicKeyResponse> getPublicKey() {
        PublicKeyResponse response = new PublicKeyResponse(
                publicKeyService.getKeyId(),
                publicKeyService.getPublicKeyPem(),
                publicKeyService.getIssuedAt());

        return ApiResponse.success(response, "Public key retrieved successfully");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String refreshToken = request != null ? request.refreshToken() : null;

        logoutUseCase.logout(
                jwt.getTokenValue(),
                jwt.getId(),
                jwt.getExpiresAt(),
                UUID.fromString(jwt.getSubject()),
                refreshToken);

        return ResponseEntity.noContent().build();
    }
}
