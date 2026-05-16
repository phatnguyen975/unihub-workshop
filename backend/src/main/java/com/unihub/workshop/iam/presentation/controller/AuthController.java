package com.unihub.workshop.iam.presentation.controller;

import com.unihub.workshop.iam.application.dto.ForgotPasswordRequest;
import com.unihub.workshop.iam.application.dto.ForgotPasswordResponse;
import com.unihub.workshop.iam.application.dto.LoginRequest;
import com.unihub.workshop.iam.application.dto.LoginResponse;
import com.unihub.workshop.iam.application.dto.RefreshRequest;
import com.unihub.workshop.iam.application.dto.RefreshResponse;
import com.unihub.workshop.iam.application.dto.RegisterRequest;
import com.unihub.workshop.iam.application.dto.RegisterResponse;
import com.unihub.workshop.iam.application.dto.ResendOtpRequest;
import com.unihub.workshop.iam.application.dto.ResetPasswordRequest;
import com.unihub.workshop.iam.application.dto.VerifyOtpRequest;
import com.unihub.workshop.iam.application.dto.VerifyOtpResponse;
import com.unihub.workshop.iam.application.usecases.AuthUseCase;
import com.unihub.workshop.iam.application.usecases.UseCaseResult;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.web.ResponseFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authenticationUseCase;
    private final ResponseFactory responseFactory;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UseCaseResult<RegisterResponse> result = authenticationUseCase.register(request);
        ApiResponse<RegisterResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        UseCaseResult<VerifyOtpResponse> result = authenticationUseCase.verifyOtp(request);
        ApiResponse<VerifyOtpResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {
        UseCaseResult<Void> result = authenticationUseCase.resendOtp(request);
        ApiResponse<Void> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        UseCaseResult<LoginResponse> result = authenticationUseCase.login(request);
        ApiResponse<LoginResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        UseCaseResult<RefreshResponse> result = authenticationUseCase.refresh(request);
        ApiResponse<RefreshResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        UseCaseResult<ForgotPasswordResponse> result = authenticationUseCase.forgotPassword(request);
        ApiResponse<ForgotPasswordResponse> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        UseCaseResult<Void> result = authenticationUseCase.resetPassword(request);
        ApiResponse<Void> body = responseFactory.success(result.data(), result.message(), result.status());
        return ResponseEntity.status(result.status()).body(body);
    }
}
